/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.event.detector;

import com.infiniteautomation.mango.spring.service.PermissionService;
import com.serotonin.json.spi.JsonProperty;
import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.detectors.AbstractEventDetectorRT;
import com.serotonin.m2m2.rt.event.detectors.PositiveCusumDetectorRT;
import com.serotonin.m2m2.view.text.TextRenderer;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
public class PositiveCusumDetectorVO extends TimeoutDetectorVO<PositiveCusumDetectorVO> {

	private static final long serialVersionUID = 1L;
	
	@JsonProperty
	private double limit;
	@JsonProperty
	private double weight;

	public PositiveCusumDetectorVO(DataPointVO vo) {
		super(vo, new int[] { DataTypes.NUMERIC });
	}
	
	public double getLimit() {
		return limit;
	}

	public void setLimit(double limit) {
		this.limit = limit;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	@Override
	public void validate(ProcessResult response, PermissionService service, PermissionHolder user) {
		super.validate(response, service, user);
		
		if(Double.isInfinite(limit) || Double.isNaN(limit))
            response.addContextualMessage("limit", "validate.invalidValue");
        if(Double.isInfinite(weight) || Double.isNaN(weight))
            response.addContextualMessage("weight", "validate.invalidValue");
	}

	@Override
	public AbstractEventDetectorRT<PositiveCusumDetectorVO> createRuntime() {
		return new PositiveCusumDetectorRT(this);
	}

	@Override
	protected TranslatableMessage getConfigurationDescription() {
        TranslatableMessage durationDesc = getDurationDescription();
        
        if (durationDesc == null)
            return new TranslatableMessage("event.detectorVo.posCusum", dataPoint.getTextRenderer().getText(
                    limit, TextRenderer.HINT_SPECIFIC));
        return new TranslatableMessage("event.detectorVo.posCusumPeriod", dataPoint.getTextRenderer()
                    .getText(limit, TextRenderer.HINT_SPECIFIC), durationDesc);
	}

}
