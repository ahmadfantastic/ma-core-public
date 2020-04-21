/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.infiniteautomation.mango.permission.MangoPermission;
import com.infiniteautomation.mango.spring.db.RoleTableDefinition;
import com.infiniteautomation.mango.util.Functions;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.infiniteautomation.mango.util.exception.ValidationException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.RoleDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;
import com.serotonin.m2m2.vo.role.RoleVO;

/**
 * @author Terry Packer
 *
 */
@Service
public class RoleService extends AbstractVOService<RoleVO, RoleTableDefinition, RoleDao> {

    @Autowired
    public RoleService(RoleDao dao, PermissionService permissionService) {
        super(dao, permissionService);
    }

    @Override
    public boolean hasEditPermission(PermissionHolder user, RoleVO vo) {
        return permissionService.hasAdminRole(user);
    }

    @Override
    public boolean hasReadPermission(PermissionHolder user, RoleVO vo) {
        return permissionService.isValidPermissionHolder(user);
    }

    @Override
    public RoleVO delete(RoleVO vo)
            throws PermissionException, NotFoundException {
        //Cannot delete the 'user' or 'superadmin' roles
        if(StringUtils.equalsIgnoreCase(vo.getXid(), getSuperadminRole().getXid())) {
            PermissionHolder user = Common.getUser();
            throw new PermissionException(new TranslatableMessage("roles.cannotAlterSuperadminRole"), user);
        }else if(StringUtils.equalsIgnoreCase(vo.getXid(), getUserRole().getXid())) {
            PermissionHolder user = Common.getUser();
            throw new PermissionException(new TranslatableMessage("roles.cannotAlterUserRole"), user);
        }
        return super.delete(vo);
    }

    @Override
    public ProcessResult validate(RoleVO vo, PermissionHolder user) {
        ProcessResult result = super.validate(vo, user);

        //Don't allow the use of role 'user' or 'superadmin'
        if(StringUtils.equalsIgnoreCase(vo.getXid(), getSuperadminRole().getXid())) {
            result.addContextualMessage("xid", "roles.cannotAlterSuperadminRole");
        }
        if(StringUtils.equalsIgnoreCase(vo.getXid(), getUserRole().getXid())) {
            result.addContextualMessage("xid", "roles.cannotAlterUserRole");
        }

        //Don't allow spaces in the XID
        Matcher matcher = Functions.WHITESPACE_PATTERN.matcher(vo.getXid());
        if(matcher.find()) {
            result.addContextualMessage("xid", "validate.role.noSpaceAllowed");
        }

        //Ensure inherited roles exist
        if(vo.getInherited() != null) {
            for(Role role : vo.getInherited()) {
                if(dao.getXidById(role.getId()) == null) {
                    result.addContextualMessage("inherited", "validate.role.notFound", role.getXid());
                }
            }
        }
        return result;
    }

    @Override
    public ProcessResult validate(RoleVO existing, RoleVO vo, PermissionHolder user) {
        ProcessResult result = this.validate(vo, user);
        if(!StringUtils.equals(existing.getXid(), vo.getXid())) {
            result.addContextualMessage("xid", "validate.role.cannotChangeXid");
        }
        return result;
    }

    /**
     *
     * @param roles
     * @param permissionType
     */
    public MangoPermission replaceAllRolesOnPermission(Set<Set<Role>> roles, PermissionDefinition def) throws ValidationException {
        PermissionHolder user = Common.getUser();
        Objects.requireNonNull(user, "Permission holder must be set in security context");
        Objects.requireNonNull(def, "Permission definition cannot be null");

        permissionService.ensureAdminRole(user);

        ProcessResult validation = new ProcessResult();
        if(roles == null) {
            validation.addContextualMessage("roles", "validate.required");
            throw new ValidationException(validation);
        }

        Set<Role> unique = new HashSet<>();
        for(Set<Role> roleSet : roles) {
            unique.addAll(roleSet);
        }

        for(Role role : unique) {
            try {
                get(role.getXid());
            }catch(NotFoundException e) {
                validation.addGenericMessage("validate.role.notFound", role.getXid());
            }
        }

        if(validation.getHasMessages()) {
            throw new ValidationException(validation);
        }

        return dao.replaceRolesOnPermission(roles, def.getPermissionTypeName());
    }

    /**
     * Get the superadmin role
     * @return
     */
    public Role getSuperadminRole() {
        return PermissionHolder.SUPERADMIN_ROLE;
    }

    /**
     * Get the default user role
     * @return
     */
    public Role getUserRole() {
        return PermissionHolder.USER_ROLE;
    }
}
