/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.infiniteautomation.mango.util.exception.ValidationException;
import com.infiniteautomation.mango.util.script.CompiledMangoJavaScript;
import com.infiniteautomation.mango.util.script.MangoJavaScript;
import com.infiniteautomation.mango.util.script.MangoJavaScriptAction;
import com.infiniteautomation.mango.util.script.MangoJavaScriptError;
import com.infiniteautomation.mango.util.script.MangoJavaScriptResult;
import com.infiniteautomation.mango.util.script.ScriptUtility;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.db.pair.IntStringPair;
import com.serotonin.io.NullWriter;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.i18n.ProcessMessage.Level;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.MangoJavascriptContextObjectDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.ScriptSourceDefinition;
import com.serotonin.m2m2.rt.dataImage.DataPointRT;
import com.serotonin.m2m2.rt.dataImage.IDataPointValueSource;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.rt.dataImage.types.AlphanumericValue;
import com.serotonin.m2m2.rt.dataImage.types.BinaryValue;
import com.serotonin.m2m2.rt.dataImage.types.DataValue;
import com.serotonin.m2m2.rt.dataImage.types.MultistateValue;
import com.serotonin.m2m2.rt.dataImage.types.NumericValue;
import com.serotonin.m2m2.rt.script.AbstractPointWrapper;
import com.serotonin.m2m2.rt.script.AlphanumericPointWrapper;
import com.serotonin.m2m2.rt.script.BinaryPointWrapper;
import com.serotonin.m2m2.rt.script.DataPointStateException;
import com.serotonin.m2m2.rt.script.DateTimeUtility;
import com.serotonin.m2m2.rt.script.ImagePointWrapper;
import com.serotonin.m2m2.rt.script.MultistatePointWrapper;
import com.serotonin.m2m2.rt.script.NumericPointWrapper;
import com.serotonin.m2m2.rt.script.ResultTypeException;
import com.serotonin.m2m2.rt.script.ScriptContextVariable;
import com.serotonin.m2m2.rt.script.ScriptError;
import com.serotonin.m2m2.rt.script.ScriptLog;
import com.serotonin.m2m2.rt.script.ScriptPermissionsException;
import com.serotonin.m2m2.rt.script.ScriptPointValueSetter;
import com.serotonin.m2m2.rt.script.UnitUtility;
import com.serotonin.m2m2.rt.script.WrapperContext;
import com.serotonin.m2m2.util.VarNames;
import com.serotonin.m2m2.util.log.LogLevel;
import com.serotonin.m2m2.util.log.NullPrintWriter;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.permission.Permissions;

import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

/**
 * Service to allow running and validating Mango JavaScript scripts 
 * 
 * @author Terry Packer
 *
 */
@SuppressWarnings("restriction")
@Service
public class MangoJavaScriptService {

    public static final String SCRIPT_PREFIX = "function __scriptExecutor__() {";
    public static final String SCRIPT_SUFFIX = " } __scriptExecutor__();";

    public static final String WRAPPER_CONTEXT_KEY = "CONTEXT";
    public static final String POINTS_CONTEXT_KEY = "POINTS";
    public static final String POINTS_MAP_KEY = "CONTEXT_POINTS";
    public static final String TIMESTAMP_CONTEXT_KEY = "TIMESTAMP";
    public static final DataValue UNCHANGED = new BinaryValue(false);
    public static final String UNCHANGED_KEY = "UNCHANGED";

    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM YYYY HH:mm:ss z");
    
    private static final Object globalFunctionsLock = new Object();

    public MangoJavaScriptService() {

    }
    
    /**
     * Validate a script with its parts
     * @param vo
     * @param user
     * @return
     */
    public ProcessResult validate(MangoJavaScript vo, PermissionHolder user) {
        ProcessResult result = new ProcessResult();
        
        Permissions.validateAddedPermissions(vo.getPermissions().getPermissionsSet(), user, result, "permissions");
        
        validateContext(vo.getContext(), user, result);
        
        if(vo.getResultDataTypeId() != null) {
            if(!DataTypes.CODES.isValidId(vo.getResultDataTypeId()))
                result.addContextualMessage("resultDataTypeId", "validate.invalidValue");
        }
        
        //Can't validate a null script
        if(StringUtils.isEmpty(vo.getScript()))
            result.addContextualMessage("script", "validate.invalidValue");
        
        return result;
    }
    
    /**
     * Validate a script context
     * @param context
     * @param user
     * @param result
     */
    public void validateContext(List<ScriptContextVariable> context, PermissionHolder user, ProcessResult result) {
        //Validate the context, can we read all points and are the var names valid
        List<String> varNameSpace = new ArrayList<String>();
        for(ScriptContextVariable var : context) {
            String varName = var.getVariableName();
            DataPointVO dp = DataPointDao.getInstance().get(var.getDataPointId());
            if(dp == null)
                result.addContextualMessage("context", "javascript.validate.missingContextPoint", varName);
            else {
                if(!Permissions.hasDataPointReadPermission(user, dp))
                    result.addContextualMessage("context", "javascript.validate.noReadPermissionOnContextPoint", varName);
            }
            if (StringUtils.isBlank(varName)) {
                result.addContextualMessage("context", "validate.allVarNames");
                continue;
            }

            if (!VarNames.validateVarName(varName)) {
                result.addContextualMessage("context", "validate.invalidVarName", varName);
                continue;
            }

            if (varNameSpace.contains(varName)) {
                result.addContextualMessage("context", "validate.duplicateVarName", varName);
                continue;
            }

            varNameSpace.add(varName);
        }

    }
    
    /**
     * Ensure the script is valid
     * @param vo
     * @param user
     * @throws ValidationException
     */
    public void ensureValid(MangoJavaScript vo, PermissionHolder user) throws ValidationException {
        Permissions.ensureDataSourcePermission(user);
        ProcessResult result = validate(vo, user);
        if(!result.isValid())
            throw new ValidationException(result);
    }
    
    /**
     * Test a script
     * @param vo
     * @param user
     * @return
     * @throws ValidationException
     * @throws PermissionException
     */
    public MangoJavaScriptResult testScript(MangoJavaScript vo, PermissionHolder user) throws ValidationException, PermissionException {
        return testScript(vo, (result, holder) ->{ return createValidationSetter(result, holder);}, user);
    }
    
    /**
     * 
     * @param vo
     * @param createSetter
     * @param user
     * @return
     */
    public MangoJavaScriptResult testScript(MangoJavaScript vo, BiFunction<MangoJavaScriptResult, PermissionHolder, ScriptPointValueSetter> createSetter, PermissionHolder user) {
        ensureValid(vo, user);
        final StringWriter scriptOut = new StringWriter();
        MangoJavaScriptResult result = new  MangoJavaScriptResult();
        try {
            final PrintWriter scriptWriter = new PrintWriter(scriptOut);
            try(ScriptLog scriptLog = new ScriptLog("scriptTest-" + user.getPermissionHolderName(), vo.getLogLevel(), scriptWriter);){
                CompiledMangoJavaScript script = new CompiledMangoJavaScript(
                        vo, createSetter.apply(result, vo.getPermissions()), scriptLog, result, this);
                
                script.compile(vo.getScript(), vo.isWrapInFunction());
                script.initialize(vo.getContext());
                
                long time = Common.timer.currentTimeMillis();
                if(vo.getResultDataTypeId() != null) {
                    script.execute(time, time, vo.getResultDataTypeId());
                    //Convert the UNCHANGED value
                    Object o = script.getResult().getResult();
                    if(o != null && o instanceof PointValueTime && ((PointValueTime)o).getValue().equals(UNCHANGED)) {
                        String unchanged;
                        if(user instanceof User) {
                            unchanged = new TranslatableMessage("eventHandlers.script.successUnchanged").translate(((User)user).getTranslations());
                        }else {
                            unchanged = new TranslatableMessage("eventHandlers.script.successUnchanged").translate(Common.getTranslations());
                        }
                        script.getResult().setResult(new PointValueTime(unchanged, ((PointValueTime)o).getTime()));
                    }
                }else
                    script.execute(time, time);
            }
        }catch (ScriptError e) {
            //The script exception should be clean as both compile() and execute() clean it
            result.addError(new MangoJavaScriptError(e.getTranslatableMessage(), e.getLineNumber(), e.getColumnNumber()));
        }catch(ResultTypeException e) {
            result.addError(new MangoJavaScriptError(e.getTranslatableMessage()));
        }catch (Exception e) {
            result.addError(new MangoJavaScriptError(e.getMessage()));
        }finally {
            result.setScriptOutput(scriptOut.toString());
        }
        return result;
    }
    
    /**
     * The preferred way to execute a script
     * @param vo
     * @param user
     * @return
     * @throws ValidationException
     * @throws PermissionException
     */
    public MangoJavaScriptResult executeScript(MangoJavaScript vo, ScriptPointValueSetter setter, PermissionHolder user) throws ValidationException, PermissionException {
        ensureValid(vo, user);
        MangoJavaScriptResult result = new MangoJavaScriptResult();
        final Writer scriptOut;
        final PrintWriter scriptWriter; 
        if(vo.isReturnLogOutput()) {
            scriptOut = new StringWriter();
            scriptWriter = new PrintWriter(scriptOut);
        }else {
            NullWriter writer = new NullWriter();
            scriptWriter = new NullPrintWriter(writer);
            scriptOut = writer;
        }
        
        try {
            try(ScriptLogExtender scriptLog = new ScriptLogExtender("scriptTest-" + user.getPermissionHolderName(), vo.getLogLevel(), scriptWriter, vo.getLog(), vo.isCloseLog());){
                
                CompiledMangoJavaScript script = new CompiledMangoJavaScript(
                        vo, setter, scriptLog, result, this);
                
                script.compile(vo.getScript(), vo.isWrapInFunction());
                script.initialize(vo.getContext());
                
                long time = Common.timer.currentTimeMillis();
                if(vo.getResultDataTypeId() != null)
                    script.execute(time, time, vo.getResultDataTypeId());
                else
                    script.execute(time, time);
            }
        }catch (ScriptError e) {
            //The script exception should be clean as both compile() and execute() clean it
            result.addError(new MangoJavaScriptError(e.getTranslatableMessage(), e.getLineNumber(), e.getColumnNumber()));
        }catch(ResultTypeException e) {
            result.addError(new MangoJavaScriptError(e.getTranslatableMessage()));
        }catch(DataPointStateException e) {
            result.addError(new MangoJavaScriptError(e.getTranslatableMessage()));
        }catch (Exception e) {
            result.addError(new MangoJavaScriptError(e.getMessage()));
        }finally {
            if(vo.isReturnLogOutput())
                result.setScriptOutput(scriptOut.toString());
        }
        return result;
    }
    
    /**
     * Compile a script to be run and add global bindings
     * 
     * @param script
     * @param wrapInFunction
     * @return
     * @throws ScriptError
     */
    public CompiledScript compile(String script, boolean wrapInFunction, PermissionHolder user) throws ScriptError {
        try {
            final ScriptEngine engine = newEngine(user);
            
            // Add constants to the context
            Bindings globalBindings = new SimpleBindings();
            
            //left here for legacy compatibility
            globalBindings.put("SECOND", Common.TimePeriods.SECONDS);
            globalBindings.put("MINUTE", Common.TimePeriods.MINUTES);
            globalBindings.put("HOUR", Common.TimePeriods.HOURS);
            globalBindings.put("DAY", Common.TimePeriods.DAYS);
            globalBindings.put("WEEK", Common.TimePeriods.WEEKS);
            globalBindings.put("MONTH", Common.TimePeriods.MONTHS);
            globalBindings.put("YEAR", Common.TimePeriods.YEARS);
            
            for(IntStringPair isp : Common.TIME_PERIOD_CODES.getIdKeys())
                globalBindings.put(Common.TIME_PERIOD_CODES.getCode(isp.getKey()), isp.getKey());
            
            for(IntStringPair isp : Common.ROLLUP_CODES.getIdKeys(Common.Rollups.NONE))
                globalBindings.put(Common.ROLLUP_CODES.getCode(isp.getKey()), isp.getKey());
            
            //Add in Additional Utilities with Global Scope
            globalBindings.put(DateTimeUtility.CONTEXT_KEY, new DateTimeUtility());
            globalBindings.put(UnitUtility.CONTEXT_KEY, new UnitUtility());
            
            engine.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);

            String toCompile;
            if(wrapInFunction) {
                toCompile = SCRIPT_PREFIX + script + SCRIPT_SUFFIX;
            }else {
                toCompile = script;
            }
            
            return ((Compilable)engine).compile(toCompile);
        }catch(ScriptException e) {
            throw ScriptError.create(e, wrapInFunction);
        }
    }
    
    /**
     * Reset the engine scope of a script and initialize for running
     * @param script
     * @param context - if provided points will be wrapped with script's setter (alternatively use script.addToContext()
     */
    public void initialize(CompiledMangoJavaScript script, Map<String, IDataPointValueSource> context) throws ScriptError, ScriptPermissionsException {
        //TODO assert compiled
        //TODO assert permissions to execute global scripts
        //TODO assert setter not null
        
        Bindings engineScope = script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
        //TODO Clear engine scope completely?
        
        engineScope.put(MangoJavaScriptService.UNCHANGED_KEY, MangoJavaScriptService.UNCHANGED);

        Set<String> points = new HashSet<String>();
        engineScope.put(MangoJavaScriptService.POINTS_CONTEXT_KEY, points);
        //Holder for modifying timestamps of meta points, in Engine Scope so it can be modified by all
        engineScope.put(MangoJavaScriptService.TIMESTAMP_CONTEXT_KEY, null);

        if(script.getPermissionHolder() != null) {
            script.getUtilities().clear();
            for(MangoJavascriptContextObjectDefinition def : ModuleRegistry.getMangoJavascriptContextObjectDefinitions()) {
                ScriptUtility util = script.isTestRun() ? def.initializeTestContextObject(script.getPermissionHolder()) : def.initializeContextObject(script.getPermissionHolder());
                util.setScriptLog(script.getLog());
                util.setResult(script.getResult());
                util.takeContext(script.getEngine(), engineScope, script.getSetter(), script.getImportExclusions(), script.isTestRun());
                engineScope.put(util.getContextKey(), util);
                script.getUtilities().add(util);
            }
            //Initialize additional utilities
            for(ScriptUtility util : script.getAdditionalUtilities()) {
                util.setScriptLog(script.getLog());
                util.setResult(script.getResult());
                util.takeContext(script.getEngine(), engineScope, script.getSetter(), script.getImportExclusions(), script.isTestRun());
                engineScope.put(util.getContextKey(), util);
            }
        }
        
        Set<Entry<String,Object>> entries = script.getAdditionalContext().entrySet();
        for(Entry<String,Object> entry: entries)
            engineScope.put(entry.getKey(), entry.getValue());
        
        if(context != null) {
            for (String varName : context.keySet()) {
                IDataPointValueSource point = context.get(varName);
                engineScope.put(varName, wrapPoint(script.getEngine(), point, script.getSetter()));
                points.add(varName); 
            }
            engineScope.put(MangoJavaScriptService.POINTS_MAP_KEY, context);
        }else
            engineScope.put(MangoJavaScriptService.POINTS_MAP_KEY, new HashMap<>());
        
        //Set the print writer and log
        script.getEngine().getContext().setWriter(script.getLog().getStdOutWriter());
        engineScope.put(ScriptLog.CONTEXT_KEY, script.getLog());

        try {
            script.getEngine().eval(getGlobalFunctions());
        } catch (ScriptException e) {
            throw ScriptError.create(e, script.isWrapInFunction());
        } catch (RuntimeException e) {
            // Nashorn seems to like to wrap exceptions in RuntimeException
            if (e.getCause() instanceof ScriptPermissionsException)
                throw (ScriptPermissionsException) e.getCause();
            else if (e.getCause() != null)
                throw ScriptError.createFromThrowable(e.getCause());
            else
                throw new ShouldNeverHappenException(e);
        }
    }
    
    /**
     * Reset result and execute script for any type of result
     * 
     * @param script
     * @param runtime
     * @param timestamp
     * @throws ScriptError
     * @throws ResultTypeException
     * @throws ScriptPermissionsException
     */
    public void execute(CompiledMangoJavaScript script, long runtime, long timestamp) throws ScriptError, ScriptPermissionsException {
        //TODO Check permissions
        script.getResult().reset();
        try {
            //Setup the wraper context
            Bindings engineScope = script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
            engineScope.put(MangoJavaScriptService.WRAPPER_CONTEXT_KEY, new WrapperContext(runtime, timestamp));
            
            //Ensure the result is available to the utilities
            for(ScriptUtility util : script.getUtilities()) {
                util.setResult(script.getResult());
            }
            
            //Initialize additional utilities
            for(ScriptUtility util : script.getAdditionalUtilities())
                util.setResult(script.getResult());
            
            Object resultObject = script.getCompiledScript().eval();
            script.getResult().setResult(resultObject);
        }catch(ScriptException e) {
            throw ScriptError.create(e, script.isWrapInFunction());
        }catch (RuntimeException e) {
            //Nashorn seems to like to wrap exceptions in RuntimeException 
            if(e.getCause() instanceof ScriptPermissionsException)
                throw (ScriptPermissionsException)e.getCause();
            else
                throw new ShouldNeverHappenException(e);
        }
    }
    
    /**
     * Reset the result and execute for PointValueTime result
     * @param script
     * @param runtime
     * @param timestamp
     * @param resultDataTypeId
     * @throws ScriptError
     * @throws ResultTypeException
     * @throws ScriptPermissionsException
     */
    public void execute(CompiledMangoJavaScript script, long runtime, long timestamp, Integer resultDataTypeId) throws ScriptError, ResultTypeException, ScriptPermissionsException {
        //TODO check permissions?
        execute(script, runtime, timestamp);

        Object ts = script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE).get(MangoJavaScriptService.TIMESTAMP_CONTEXT_KEY);
        if (ts != null) {
            // Check the type of the object.
            if (ts instanceof Number)
                // Convert to long
                timestamp = ((Number) ts).longValue();
        }
        Object resultObject = script.getResult().getResult();
        DataValue value = coerce(resultObject, resultDataTypeId);
        script.getResult().setResult(new PointValueTime(value, timestamp));

    }
    
    /**
     * Create a dumb setter that tracks actions but does not actually set anything
     * @param vo
     * @param result
     * @param permissions
     * @return
     */
    public ScriptPointValueSetter createValidationSetter(MangoJavaScriptResult result, PermissionHolder permissions) {
       return new ScriptPointValueSetter(permissions) {
            
           @Override
            public void set(IDataPointValueSource point, Object value, long timestamp, String annotation) {
                DataPointRT dprt = (DataPointRT) point;
                if(!dprt.getVO().getPointLocator().isSettable()) {
                    result.addAction(new MangoJavaScriptAction(new TranslatableMessage("javascript.validate.pointNotSettable", dprt.getVO().getExtendedName()), Level.error));
                    return;
                }

                if(!Permissions.hasDataPointSetPermission(permissions, dprt.getVO())) {
                    result.addAction(new MangoJavaScriptAction(new TranslatableMessage("javascript.validate.pointPermissionsFailure", dprt.getVO().getXid()), Level.warning));
                    return;
                }
                if(annotation != null)
                    result.addAction(new MangoJavaScriptAction(new TranslatableMessage("javascript.validate.setPointValueAnnotation", dprt.getVO().getExtendedName(), value, sdf.format(new Date(timestamp)), annotation)));
                else
                    result.addAction(new MangoJavaScriptAction(new TranslatableMessage("javascript.validate.setPointValue", dprt.getVO().getExtendedName(), value, sdf.format(new Date(timestamp)))));
            }

            @Override
            protected void setImpl(IDataPointValueSource point, Object value, long timestamp, String annotation) {
                //not really setting
            }
        };

    }
    
    /* Utilities for Script Execution */
    /**
     * Create a new script engine
     * @param - to help restrict script execution access so that only admin can access java classes
     * @return
     */
    public ScriptEngine newEngine(PermissionHolder holder) {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        if(holder != null && holder.hasAdminPermission())
            return factory.getScriptEngine();
        else
            return factory.getScriptEngine(new NoJavaFilter());
    }
    
    private static class NoJavaFilter implements ClassFilter {

        @Override
        public boolean exposeToScripts(String s) {
            return false;
        }
    }
    
    /**
     * Wrap a data point for insertion into script context
     * @param engine
     * @param point
     * @param setter
     * @return
     */
    public AbstractPointWrapper wrapPoint(ScriptEngine engine, IDataPointValueSource point,
            ScriptPointValueSetter setter) {
        int dt = point.getDataTypeId();
        if (dt == DataTypes.BINARY)
            return new BinaryPointWrapper(point, engine, setter);
        if (dt == DataTypes.MULTISTATE)
            return new MultistatePointWrapper(point, engine, setter);
        if (dt == DataTypes.NUMERIC)
            return new NumericPointWrapper(point, engine, setter);
        if (dt == DataTypes.ALPHANUMERIC)
            return new AlphanumericPointWrapper(point, engine, setter);
        if (dt == DataTypes.IMAGE)
            return new ImagePointWrapper(point, engine, setter);
        throw new ShouldNeverHappenException("Unknown data type id: " + point.getDataTypeId());
    }
    
    private static String FUNCTIONS;

    /**
     * Get all Module defined functions
     * @return
     */
    public String getGlobalFunctions() {
        synchronized(globalFunctionsLock) {
            if (FUNCTIONS == null) {
                StringWriter sw = new StringWriter();
                List<ScriptSourceDefinition> defs = ModuleRegistry.getDefinitions(ScriptSourceDefinition.class);
                for (ScriptSourceDefinition def : defs) {
                    for (String s : def.getScripts())
                        sw.append(s).append("\r\n");
                }
                FUNCTIONS = sw.toString();
            }
            return FUNCTIONS;
        }
    }

    /**
     * Clear all functions so they are re-loaded on next 'get'
     */
    public void clearGlobalFunctions() {
        synchronized(globalFunctionsLock) {
            FUNCTIONS = null;
        }
    }
    
    /**
     * Add a data point to the engine scope bindings.
     * 
     * Only to be called while script is not executing.
     * 
     * @param engine
     * @param varName
     * @param dprt
     * @param setCallback
     */
    @SuppressWarnings("unchecked")
    public void addToContext(ScriptEngine engine, String varName, DataPointRT dprt, ScriptPointValueSetter setCallback) {
        AbstractPointWrapper wrapper = wrapPoint(engine, dprt, setCallback);
        engine.put(varName, wrapper);
        
        Map<String, IDataPointValueSource> context = (Map<String, IDataPointValueSource>)engine.getBindings(ScriptContext.ENGINE_SCOPE).get(POINTS_MAP_KEY);
        context.put(varName, dprt);
        
        Set<String> points = (Set<String>) engine.get(POINTS_CONTEXT_KEY);
        if (points != null) {
            points.remove(varName);
            points.add(varName);
        }
    }
    
    /**
     * Remove a data point from the engine scope bindings.
     * 
     * Only to be called while the script is not executing.
     * @param engine
     * @param varName
     */
    @SuppressWarnings("unchecked")
    public void removeFromContext(ScriptEngine engine, String varName) {
        
        Map<String, IDataPointValueSource> context = (Map<String, IDataPointValueSource>)engine.getBindings(ScriptContext.ENGINE_SCOPE).get(POINTS_MAP_KEY);
        context.remove(varName);
        
        Set<String> points = (Set<String>) engine.get(POINTS_CONTEXT_KEY);
        if (points != null) {
            points.remove(varName);
        }
        engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(varName);
    }
    

    
    /**
     * Coerce an object into a DataValue
     * @param input
     * @param toDataTypeId
     * @return
     * @throws ResultTypeException
     */
    public DataValue coerce(Object input, int toDataTypeId) throws ResultTypeException {
        DataValue value;
        
        if(input instanceof DataValue)
            return (DataValue)input;
        
        if (input == null) {
            if (toDataTypeId == DataTypes.BINARY)
                value = new BinaryValue(false);
            else if (toDataTypeId == DataTypes.MULTISTATE)
                value = new MultistateValue(0);
            else if (toDataTypeId == DataTypes.NUMERIC)
                value = new NumericValue(0);
            else if (toDataTypeId == DataTypes.ALPHANUMERIC)
                value = new AlphanumericValue("");
            else
                value = null;
        }
        else if (input instanceof AbstractPointWrapper) {
            value = ((AbstractPointWrapper) input).getValueImpl();
            if ((value != null)&&(value.getDataType() != toDataTypeId))
                throw new ResultTypeException(new TranslatableMessage("event.script.convertError", input,
                        DataTypes.getDataTypeMessage(toDataTypeId)));
        }
        // See if the type matches.
        else if (toDataTypeId == DataTypes.BINARY && input instanceof Boolean)
            value = new BinaryValue((Boolean) input);
        else if (toDataTypeId == DataTypes.MULTISTATE) {
            if (input instanceof Number)
                value = new MultistateValue(((Number) input).intValue());
            else if (input instanceof String) {
                try {
                    value = new MultistateValue(Integer.parseInt((String) input));
                }
                catch (NumberFormatException e) {
                    throw new ResultTypeException(new TranslatableMessage("event.script.convertError", input,
                            DataTypes.getDataTypeMessage(toDataTypeId)));
                }
            }
            else
                throw new ResultTypeException(new TranslatableMessage("event.script.convertError", input,
                        DataTypes.getDataTypeMessage(toDataTypeId)));
        }
        else if (toDataTypeId == DataTypes.NUMERIC) {
            if (input instanceof Number)
                value = new NumericValue(((Number) input).doubleValue());
            else if (input instanceof NumericValue)
                value = (NumericValue) input;
            else if (input instanceof String) {
                try {
                    value = new NumericValue(Double.parseDouble((String) input));
                }
                catch (NumberFormatException e) {
                    throw new ResultTypeException(new TranslatableMessage("event.script.convertError", input,
                            DataTypes.getDataTypeMessage(toDataTypeId)));
                }
            }
            else
                throw new ResultTypeException(new TranslatableMessage("event.script.convertError", input,
                        DataTypes.getDataTypeMessage(toDataTypeId)));
        }
        else if (toDataTypeId == DataTypes.ALPHANUMERIC)
            value = new AlphanumericValue(input.toString());
        else
            // If not, ditch it.
            throw new ResultTypeException(new TranslatableMessage("event.script.convertError", input,
                    DataTypes.getDataTypeMessage(toDataTypeId)));

        return value;
    }
    
    public SimpleDateFormat getDateFormat() {
        return sdf;
    }

    private class ScriptLogExtender extends ScriptLog {
        
        private final ScriptLog logger;
        private final boolean closeExtendedLog;
        
        /**
         * @param id
         * @param level
         * @param out
         */
        public ScriptLogExtender(String id, LogLevel level, PrintWriter out, ScriptLog logger, boolean closeExtendedLog) {
            super(id, level, out);
            this.logger = logger;
            this.closeExtendedLog = closeExtendedLog;
        }


        public void trace(Object o) {
            if(logger != null)
                logger.trace(o);
            super.trace(o);
        }

        public void debug(Object o) {
            if(logger != null)
                logger.debug(o);
            super.debug(o);
        }

        public void info(Object o) {
            if(logger != null)
                logger.info(o);
            super.info(o);
        }

        public void warn(Object o) {
            if(logger != null)
                logger.warn(o);
            super.warn(o);
        }

        public void error(Object o) {
            if(logger != null)
                logger.error(o);
            super.error(o);
        }

        public void fatal(Object o) {
            if(logger != null)
                logger.fatal(o);
            super.fatal(o);
        }
        
        public PrintWriter getStdOutWriter() {
            if(logger != null)
                return logger.getStdOutWriter();
            else
                return super.getStdOutWriter();
        }
        
        /**
         * Get the file currently being written to
         * @return
         */
        public File getFile(){
            if(logger != null)
                return logger.getFile();
            else
                return super.getFile();
        }
        
        public PrintWriter getPrintWriter() {
            if(logger != null)
                return logger.getPrintWriter();
            else
                return super.getPrintWriter();
        }
        
        public void close() {
            if(logger != null && closeExtendedLog)
                logger.close();
            else
                super.close();
        }

        public String getId() {
            if(logger != null)
                return logger.getId();
            else
                return super.getId();
                    
        }

        public LogLevel getLogLevel() {
            if(logger != null)
                return logger.getLogLevel();
            else
                return super.getLogLevel();
        }

        public void setLogLevel(LogLevel logLevel) {
            if(logger != null)
                logger.setLogLevel(logLevel);
            super.setLogLevel(logLevel);
        }

        //
        // Trace
        public boolean isTraceEnabled() {
            if(logger != null && logger.isTraceEnabled())
                return true;
            else
                return super.isTraceEnabled();
        }

        public void trace(String s) {
            if(logger != null)
                logger.trace(s);
            super.trace(s);
        }

        public void trace(Throwable t) {
            if(logger != null)
                logger.trace(t);
            super.trace(t);
        }

        public void trace(String s, Throwable t) {
            if(logger != null)
                logger.trace(s, t);
            super.trace(s, t);
        }

        //
        // Debug
        public boolean isDebugEnabled() {
            if(logger != null && logger.isDebugEnabled())
                return true;
            else
                return super.isDebugEnabled();
        }

        public void debug(String s) {
            if(logger != null)
                logger.debug(s);
            super.debug(s);
        }

        public void debug(Throwable t) {
            if(logger != null)
                logger.debug(t);
            super.debug(t);
        }

        public void debug(String s, Throwable t) {
            if(logger != null)
                logger.debug(s, t);
            super.debug(s, t);
        }

        //
        // Info
        public boolean isInfoEnabled() {
            if(logger != null && logger.isInfoEnabled())
                return true;
            else
                return super.isInfoEnabled();
        }

        public void info(String s) {
            if(logger != null)
                logger.info(s);
            super.info(s);
        }

        public void info(Throwable t) {
            if(logger != null)
                logger.info(t);
            super.info(t);
        }

        public void info(String s, Throwable t) {
            if(logger != null)
                logger.info(s, t);
            super.info(s, t);
        }

        //
        // Warn
        public boolean isWarnEnabled() {
            if(logger != null && logger.isWarnEnabled())
                return true;
            else
                return super.isWarnEnabled();
        }

        public void warn(String s) {
            if(logger != null)
                logger.warn(s);
            super.warn(s);
        }

        public void warn(Throwable t) {
            if(logger != null)
                logger.warn(t);
            super.warn(t);
        }

        public void warn(String s, Throwable t) {
            if(logger != null)
                logger.warn(s, t);
            super.warn(s, t);
        }

        //
        // Error
        public boolean isErrorEnabled() {
            if(logger != null && logger.isErrorEnabled())
                return true;
            else
                return super.isErrorEnabled();
        }

        public void error(String s) {
            if(logger != null)
                logger.error(s);
            super.error(s);
        }

        public void error(Throwable t) {
            if(logger != null)
                logger.error(t);
            super.error(t);
        }

        public void error(String s, Throwable t) {
            if(logger != null)
                logger.error(s, t);
            super.error(s, t);
        }

        //
        // Fatal
        public boolean isFatalEnabled() {
            if(logger != null && logger.isFatalEnabled())
                return true;
            else
                return super.isFatalEnabled();
        }

        public void fatal(String s) {
            if(logger != null)
                logger.fatal(s);
            super.fatal(s);
        }

        public void fatal(Throwable t) {
            if(logger != null)
                logger.fatal(t);
            super.fatal(t);
        }

        public void fatal(String s, Throwable t) {
            if(logger != null)
                logger.fatal(s, t);
            super.fatal(s, t);
        }

        
        public boolean trouble() {
            if(logger != null)
                return logger.trouble();
            else
                return super.trouble();
        }
        
        /**
         * List all the files
         * @return
         */
        public File[] getFiles(){
            if(logger != null)
                return logger.getFiles();
            else
                return super.getFiles();
        }
    }
}
