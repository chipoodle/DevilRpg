package com.chipoodle.devilrpg.capability.tamable_minion;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.entity.ITamableEntity;


public interface TamableMinionCapabilityInterface extends IGenericCapability {

    void applyPassives(ITamableEntity entity);


}
