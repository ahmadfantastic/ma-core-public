/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.event.detector;

import java.io.IOException;

import com.infiniteautomation.mango.spring.service.PermissionService;
import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.spi.JsonProperty;
import com.serotonin.json.type.JsonObject;
import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.detectors.AbstractEventDetectorRT;
import com.serotonin.m2m2.rt.event.detectors.AnalogRangeDetectorRT;
import com.serotonin.m2m2.view.text.TextRenderer;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
public class AnalogRangeDetectorVO extends TimeoutDetectorVO<AnalogRangeDetectorVO>{

	private static final long serialVersionUID = 1L;
	
	@JsonProperty
	private double low;
	@JsonProperty
	private double high;
	@JsonProperty
	private boolean withinRange;

	public AnalogRangeDetectorVO(DataPointVO vo) {
		super(vo, new int[] { DataTypes.NUMERIC });
	}
	
	public double getLow() {
		return low;
	}

	public void setLow(double low) {
		this.low = low;
	}

	public double getHigh() {
		return high;
	}

	public void setHigh(double high) {
		this.high = high;
	}

	public boolean isWithinRange() {
		return withinRange;
	}

	public void setWithinRange(boolean withinRange) {
		this.withinRange = withinRange;
	}
	
	@Override
	public void validate(ProcessResult response, PermissionService service, PermissionHolder user) {
		super.validate(response, service, user);
		
		if(high <= low)
			response.addContextualMessage("high", "validate.greaterThan", low);
	}

	@Override
	public AbstractEventDetectorRT<AnalogRangeDetectorVO> createRuntime() {
		return new AnalogRangeDetectorRT(this);
	}

	@Override
	protected TranslatableMessage getConfigurationDescription() {
		TranslatableMessage durationDesc = getDurationDescription();
		
        //For within range
        if (withinRange) {
            if (durationDesc == null)
                return new TranslatableMessage("event.detectorVo.range", dataPoint.getTextRenderer().getText(
                        low, TextRenderer.HINT_SPECIFIC), dataPoint.getTextRenderer().getText(high,
                        TextRenderer.HINT_SPECIFIC));
            return new TranslatableMessage("event.detectorVo.rangePeriod", dataPoint.getTextRenderer()
                        .getText(low, TextRenderer.HINT_SPECIFIC), dataPoint.getTextRenderer().getText(high,
                        TextRenderer.HINT_SPECIFIC), durationDesc);
        }
        else {
            //Outside of range
            if (durationDesc == null)
                return new TranslatableMessage("event.detectorVo.rangeOutside", dataPoint.getTextRenderer()
                        .getText(low, TextRenderer.HINT_SPECIFIC), dataPoint.getTextRenderer().getText(high,
                        TextRenderer.HINT_SPECIFIC));
            return new TranslatableMessage("event.detectorVo.rangeOutsidePeriod", dataPoint
                        .getTextRenderer().getText(low, TextRenderer.HINT_SPECIFIC), dataPoint.getTextRenderer()
                        .getText(high, TextRenderer.HINT_SPECIFIC), durationDesc);
        }
	}
	
    @Override
    public void jsonWrite(ObjectWriter writer) throws IOException, JsonException {
    	super.jsonWrite(writer);
    }

    @Override
    public void jsonRead(JsonReader reader, JsonObject jsonObject) throws JsonException {
    	super.jsonRead(reader, jsonObject);
    }
}
