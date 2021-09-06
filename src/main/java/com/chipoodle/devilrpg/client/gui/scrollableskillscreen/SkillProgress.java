package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.minecraft.advancements.CriterionProgress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class SkillProgress implements Comparable<SkillProgress> {
	private final Map<String, CriterionProgress> criteria = Maps.newHashMap();
	private String[][] requirements = new String[0][];
	private int skillPoint;
	private int maxSkillPoint;

	public SkillProgress(int skillPoint, int maxSkillPoint) {
		update(skillPoint, maxSkillPoint);
	}

	
	public void update(int skillPoint, int maxSkillPoint) {
		this.skillPoint = skillPoint;
		this.maxSkillPoint = maxSkillPoint;
	}
	
	/**
	 * Update this ScrollableSkillProgress' criteria and requirements
	 */
	/*public void update(Map<String, Criterion> criteriaIn, String[][] requirements) {
		DevilRpg.LOGGER.info("update criteriaIn {} requirements {}",criteriaIn,requirements);
		Set<String> set = criteriaIn.keySet();
		this.criteria.entrySet().removeIf((criteriaEntry) -> {
			return !set.contains(criteriaEntry.getKey());
		});

		for (String s : set) {
			if (!this.criteria.containsKey(s)) {
				this.criteria.put(s, new CriterionProgress());
			}
		}

		this.requirements = requirements;
	}*/

	public boolean isDone() {
		return skillPoint >= maxSkillPoint;
		/*
		 * if (this.requirements.length == 0) { return false; } else { for (String[]
		 * astring : this.requirements) { boolean flag = false;
		 * 
		 * for (String s : astring) { CriterionProgress criterionprogress =
		 * this.getCriterionProgress(s); if (criterionprogress != null &&
		 * criterionprogress.isDone()) { flag = true; break; } }
		 * 
		 * if (!flag) { return false; } }
		 * 
		 * return true; }
		 */
	}

	public boolean hasProgress() {
		return skillPoint > 0;
		/*
		 * for (CriterionProgress criterionprogress : this.criteria.values()) { if
		 * (criterionprogress.isDone()) { return true; } }
		 * 
		 * return false; }
		 * 
		 * public boolean grantCriterion(String criterionIn) { CriterionProgress
		 * criterionprogress = this.criteria.get(criterionIn); if (criterionprogress !=
		 * null && !criterionprogress.isDone()) { criterionprogress.grant(); return
		 * true; } else { return false; }
		 */
	}

	public boolean revokeCriterion(String criterionIn) {
		CriterionProgress criterionprogress = this.criteria.get(criterionIn);
		if (criterionprogress != null && criterionprogress.isDone()) {
			criterionprogress.revoke();
			return true;
		} else {
			return false;
		}
	}

	public String toString() {
		return "ScrollableSkillProgress{criteria=" + this.criteria + ", requirements="
				+ Arrays.deepToString(this.requirements) + '}';
	}

	/*public void serializeToNetwork(PacketBuffer buffer) {
		buffer.writeVarInt(this.criteria.size());

		for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
			buffer.writeUtf(entry.getKey());
			entry.getValue().serializeToNetwork(buffer);
		}

	}*/

	/*
	 * public static SkillProgress fromNetwork(PacketBuffer buffer) { SkillProgress
	 * advancementprogress = new SkillProgress(); int i = buffer.readVarInt();
	 * 
	 * for (int j = 0; j < i; ++j) {
	 * advancementprogress.criteria.put(buffer.readUtf(32767),
	 * CriterionProgress.fromNetwork(buffer)); }
	 * 
	 * return advancementprogress; }
	 */

	/*
	 * @Nullable public CriterionProgress getCriterionProgress(String criterionIn) {
	 * return this.criteria.get(criterionIn); }
	 */

	@OnlyIn(Dist.CLIENT)
	public float getPercent() {

		if (maxSkillPoint == 0)
			return 0.0f;

		return skillPoint / (float) maxSkillPoint;

		/*
		 * if (this.criteria.isEmpty()) { return 0.0F; } else { float f = (float)
		 * this.requirements.length; float f1 = (float)
		 * this.countCompletedRequirements(); return f1 / f; }
		 */
	}

	@Nullable
	@OnlyIn(Dist.CLIENT)
	public String getProgressText() {
		if (this.criteria.isEmpty()) {
			return null;
		} else {
			int i = this.requirements.length;
			if (i <= 1) {
				return null;
			} else {
				int j = this.countCompletedRequirements();
				return j + "/" + i;
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	private int countCompletedRequirements() {
		return skillPoint;
		/*
		 * int i = 0;
		 * 
		 * for (String[] astring : this.requirements) { boolean flag = false;
		 * 
		 * for (String s : astring) { CriterionProgress criterionprogress =
		 * this.getCriterionProgress(s); if (criterionprogress != null &&
		 * criterionprogress.isDone()) { flag = true; break; } }
		 * 
		 * if (flag) { ++i; } }
		 * 
		 * return i;
		 */
	}

	public Iterable<String> getRemaningCriteria() {
		List<String> list = Lists.newArrayList();

		for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
			if (!entry.getValue().isDone()) {
				list.add(entry.getKey());
			}
		}

		return list;
	}

	public Iterable<String> getCompletedCriteria() {
		List<String> list = Lists.newArrayList();

		for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
			if (entry.getValue().isDone()) {
				list.add(entry.getKey());
			}
		}

		return list;
	}

	@Nullable
	public Date getFirstProgressDate() {
		Date date = null;

		for (CriterionProgress criterionprogress : this.criteria.values()) {
			if (criterionprogress.isDone() && (date == null || criterionprogress.getObtained().before(date))) {
				date = criterionprogress.getObtained();
			}
		}

		return date;
	}

	public int compareTo(SkillProgress p_compareTo_1_) {
		Date date = this.getFirstProgressDate();
		Date date1 = p_compareTo_1_.getFirstProgressDate();
		if (date == null && date1 != null) {
			return 1;
		} else if (date != null && date1 == null) {
			return -1;
		} else {
			return date == null && date1 == null ? 0 : date.compareTo(date1);
		}
	}

	/*
	 * public static class Serializer implements JsonDeserializer<SkillProgress>,
	 * JsonSerializer<SkillProgress> { public JsonElement serialize(SkillProgress
	 * p_serialize_1_, Type p_serialize_2_, JsonSerializationContext p_serialize_3_)
	 * { JsonObject jsonobject = new JsonObject(); JsonObject jsonobject1 = new
	 * JsonObject();
	 * 
	 * for (Entry<String, CriterionProgress> entry :
	 * p_serialize_1_.criteria.entrySet()) { CriterionProgress criterionprogress =
	 * entry.getValue(); if (criterionprogress.isDone()) {
	 * jsonobject1.add(entry.getKey(), criterionprogress.serializeToJson()); } }
	 * 
	 * if (!jsonobject1.entrySet().isEmpty()) { jsonobject.add("criteria",
	 * jsonobject1); }
	 * 
	 * jsonobject.addProperty("done", p_serialize_1_.isDone()); return jsonobject; }
	 * 
	 * public SkillProgress deserialize(JsonElement p_deserialize_1_, Type
	 * p_deserialize_2_, JsonDeserializationContext p_deserialize_3_) throws
	 * JsonParseException { JsonObject jsonobject =
	 * JSONUtils.convertToJsonObject(p_deserialize_1_, "advancement"); JsonObject
	 * jsonobject1 = JSONUtils.getAsJsonObject(jsonobject, "criteria", new
	 * JsonObject()); SkillProgress advancementprogress = new SkillProgress();
	 * 
	 * for (Entry<String, JsonElement> entry : jsonobject1.entrySet()) { String s =
	 * entry.getKey(); advancementprogress.criteria.put(s,
	 * CriterionProgress.fromJson(JSONUtils.convertToString(entry.getValue(), s)));
	 * }
	 * 
	 * return advancementprogress; } }
	 */

}
