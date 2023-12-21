package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;


public class SkillElement {
	private final SkillElement parent;
	private final SkillDisplayInfo display;
	private final SkillElementRewards rewards;
	private final ResourceLocation id;
	private final Set<SkillElement> children = Sets.newLinkedHashSet();
	private final Component displayText;
	private final SkillEnum skillCapability;
	private final SkillResourceCost skillResourceCost;

	public SkillElement(ResourceLocation id, @Nullable SkillElement parentIn, @Nullable SkillDisplayInfo displayIn,
			SkillElementRewards rewardsIn, SkillEnum skillCapability, SkillResourceCost skillResourceCost) {
		this.id = id;
		this.display = displayIn;
		this.parent = parentIn;
		this.rewards = rewardsIn;
		if (parentIn != null) {
			parentIn.addChild(this);
		}

		if (displayIn == null) {
			this.displayText = Component.literal(id.toString());
		} else {
			ChatFormatting textformatting = displayIn.getFrame().getFormat();
			Component itextcomponent = displayIn.getTitle();
			Component itextcomponent1 = ComponentUtils
					.mergeStyles(itextcomponent.copy(), Style.EMPTY.applyFormat(textformatting))
					.append("\n").append(displayIn.getDescription());
			Component itextcomponent2 = itextcomponent.copy().withStyle((style) -> {
				return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, itextcomponent1));
			});
			this.displayText = ComponentUtils.wrapInSquareBrackets(itextcomponent2).withStyle(textformatting);
		}
		this.skillCapability = skillCapability;
		this.skillResourceCost = skillResourceCost;
	}

	/**
	 * Creates a new advancement builder with the data from this advancement
	 */
	public SkillElement.Builder copy() {
		return new SkillElement.Builder(this.id, this.parent == null ? null : this.parent.getId(), this.display,
				this.rewards, this.skillCapability,this.skillResourceCost);
	}

	/**
	 * Get the {@linkplain SkillElement} that is this {@code SkillElement}'s parent. This
	 * determines the tree structure
	 * 
	 * @return the parent {@code SkillElement} of this {@code SkillElement}, or
	 *         {@code null} to signify that this {@code
	 * SkillElement} is a root with no parent.
	 */
	@Nullable
	public SkillElement getParent() {
		return this.parent;
	}

	/**
	 * Get information that defines this {@code SkillElement}'s appearance in GUIs.
	 * 
	 * @return information that defines this {@code SkillElement}'s appearance in
	 *         GUIs. If {@code null}, signifies an invisible {@code SkillElement}.
	 */
	@Nullable
	public SkillDisplayInfo getDisplay() {
		return this.display;
	}

	public SkillElementRewards getRewards() {
		return this.rewards;
	}

	@Override
	public String toString() {
		StringBuilder builder2 = new StringBuilder();
		builder2.append("SkillElement [");
		if (parent != null)
			builder2.append("parent=").append(parent).append(", ");
		if (display != null)
			builder2.append("display=").append(display).append(", ");
		if (rewards != null)
			builder2.append("rewards=").append(rewards).append(", ");
		if (id != null)
			builder2.append("id=").append(id).append(", ");
		if (children != null)
			builder2.append("children=").append(children).append(", ");
		if (displayText != null)
			builder2.append("displayText=").append(displayText).append(", ");
		if (skillCapability != null)
			builder2.append("skillCapability=").append(skillCapability).append(", ");
		if (skillResourceCost != null)
			builder2.append("skillManaCost=").append(skillResourceCost);
		builder2.append("]");
		return builder2.toString();
	}

	/**
	 * Get the children of this {@code SkillElement}.
	 * 
	 * @return an {@code Iterable} of this {@code SkillElement}'s children.
	 * @see #getParent()
	 */
	public Iterable<SkillElement> getChildren() {
		return this.children;
	}

	/**
	 * Add the given {@code SkillElement} as a child of this {@code SkillElement}.
	 * 
	 * @see #getParent()
	 */
	public void addChild(SkillElement advancementIn) {
		this.children.add(advancementIn);
	}

	/**
	 * Get this {@code SkillElement}'s unique identifier.
	 * 
	 * @return this {@code SkillElement}'s unique identifier
	 */
	public ResourceLocation getId() {
		return this.id;
	}

	public boolean equals(Object p_equals_1_) {
		if (this == p_equals_1_) {
			return true;
		} else if (!(p_equals_1_ instanceof SkillElement)) {
			return false;
		} else {
			SkillElement advancement = (SkillElement) p_equals_1_;
			return this.id.equals(advancement.id);
		}
	}

	public int hashCode() {
		return this.id.hashCode();
	}

	/**
	 * Returns the {@code ITextComponent} that is shown in the chat message sent
	 * after this {@code SkillElement} is completed.
	 * 
	 * @return the {@code ITextComponent} that is shown in the chat message sent
	 *         after this {@code SkillElement} is completed. If this
	 *         {@code SkillElement} is {@linkplain #getDisplay() invisible}, then it
	 *         consists simply of {@link #getId()}. Otherwise, it is the
	 *         {@linkplain DisplayInfo#getTitle() title} inside square brackets,
	 *         colored by the
	 *         {@linkplain FrameType#getChatColor()}  frame
	 *         type}, and hovering over it shows the
	 *         {@linkplain DisplayInfo#getDescription() description}.
	 */
	public Component getDisplayText() {
		return this.displayText;
	}
	
	public SkillEnum getSkillCapability() {
		return skillCapability;
	}
		
	public SkillResourceCost getSkillManaCost() {
		return skillResourceCost;
	}

	public static class Builder {
		private ResourceLocation id;
		private ResourceLocation parentId;
		private SkillElement parent;
		private SkillDisplayInfo display;
		private SkillElementRewards rewards = SkillElementRewards.EMPTY;
		private SkillEnum skillCapability;
		private SkillResourceCost skillResourceCost;

		private Builder(ResourceLocation id, @Nullable ResourceLocation parentIdIn,
				@Nullable SkillDisplayInfo displayIn, SkillElementRewards rewardsIn, SkillEnum skillCapability, SkillResourceCost skillResourceCost) {
			this.parentId = parentIdIn;
			this.id = id;
			this.display = displayIn;
			this.rewards = rewardsIn;
			this.skillCapability = skillCapability;
			this.skillResourceCost = skillResourceCost;
		}

		private Builder() {
		}

		public static SkillElement.Builder builder() {
			return new SkillElement.Builder();
		}

		public SkillElement.Builder withParent(SkillElement parentIn) {
			this.parent = parentIn;
			return this;
		}

		public SkillElement.Builder withParentId(ResourceLocation parentIdIn) {
			this.parentId = parentIdIn;
			return this;
		}

		public SkillElement.Builder withDisplay(ItemStack stack, Component title, Component description,
												@Nullable ResourceLocation background, @Nullable ResourceLocation image, SkillFrameType frame, boolean showToast,
												boolean announceToChat, boolean hidden) {
			return this.withDisplay(new SkillDisplayInfo(stack, title, description, background, image,frame, showToast,
					announceToChat, hidden));
		}

		public SkillElement.Builder withDisplay(ItemLike itemIn, Component title, Component description,
												@Nullable ResourceLocation background, @Nullable ResourceLocation image, SkillFrameType frame, boolean showToast,
												boolean announceToChat, boolean hidden) {
			return this.withDisplay(new SkillDisplayInfo(new ItemStack(itemIn.asItem()), title, description, background,image,
					frame, showToast, announceToChat, hidden));
		}

		public SkillElement.Builder withDisplay(SkillDisplayInfo displayIn) {
			this.display = displayIn;
			return this;
		}

		public SkillElement.Builder withRewards(SkillElementRewards.Builder rewardsBuilder) {
			return this.withRewards(rewardsBuilder.build());
		}

		public SkillElement.Builder withRewards(SkillElementRewards rewards) {
			this.rewards = rewards;
			return this;
		}

		public SkillElement.Builder withCapability(SkillEnum skillCapability) {
			this.skillCapability = skillCapability;
			return this;
		}
		
		public SkillElement.Builder withSkillManaCost(SkillResourceCost skillResourceCost) {
			this.skillResourceCost = skillResourceCost;
			return this;
		}
		
		/**
		 * Tries to resolve the parent of this advancement, if possible. Returns true on
		 * success.
		 */
		public boolean resolveParent(Function<ResourceLocation, SkillElement> lookup) {
			if (this.parentId == null) {
				return true;
			} else {
				if (this.parent == null) {
					this.parent = lookup.apply(this.parentId);
					//DevilRpg.LOGGER.info("|----Id: " + this.id + "  " + "ParentId: " + this.parentId);

				}

				return this.parent != null;
			}
		}

		public SkillElement build(ResourceLocation id) {
			if (!this.resolveParent((parentID) -> null)) {
				throw new IllegalStateException("Tried to build incomplete skill!");
			} else {
				return new SkillElement(id, this.parent, this.display, this.rewards, this.skillCapability,this.skillResourceCost);
			}
		}

		public SkillElement register(Consumer<SkillElement> consumer, String id) {
			SkillElement skillElement = this.build(new ResourceLocation(id));
			consumer.accept(skillElement);
			return skillElement;
		}

		public JsonObject serialize() {
			JsonObject jsonobject = new JsonObject();
			if (this.parent != null) {
				jsonobject.addProperty("parent", this.parent.getId().toString());
			} else if (this.parentId != null) {
				jsonobject.addProperty("parent", this.parentId.toString());
			}

			if (this.display != null) {
				jsonobject.add("display", this.display.serialize());
			}

			jsonobject.add("rewards", this.rewards.serialize());

			return jsonobject;
		}

		public void writeTo(FriendlyByteBuf buf) {
			if (this.parentId == null) {
				buf.writeBoolean(false);
			} else {
				buf.writeBoolean(true);
				buf.writeResourceLocation(this.parentId);
			}

			if (this.display == null) {
				buf.writeBoolean(false);
			} else {
				buf.writeBoolean(true);
				this.display.write(buf);
			}
		}

		public String toString() {
			return "Task SkillElement{parentId=" + this.parentId + ", display=" + this.display + ", rewards="
					+ this.rewards + ", criteria=" + '}';
		}

		public static SkillElement.Builder deserialize(JsonObject json) {
			try {
				if (!json.has("id"))
					throw new JsonSyntaxException("SkillElement id cannot be empty");

				ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(json, "id"));

				ResourceLocation resourcelocation = json.has("parent")
						? new ResourceLocation(GsonHelper.getAsString(json, "parent"))
						: null;

				SkillDisplayInfo displayinfo = json.has("display")
						? SkillDisplayInfo.deserialize(GsonHelper.getAsJsonObject(json, "display"))
						: null;

				SkillElementRewards advancementrewards = json.has("rewards")
						? SkillElementRewards.deserializeRewards(GsonHelper.getAsJsonObject(json, "rewards"))
						: SkillElementRewards.EMPTY;

				SkillEnum skillCapability = json.has("capability")
						? SkillEnum.getByJsonName(GsonHelper.getAsString(json, "capability"))
						: SkillEnum.EMPTY;
				
				SkillResourceCost skillResourceCost = json.has("skillmanacost")
						?  SkillResourceCost.deserialize(GsonHelper.getAsJsonObject(json, "skillmanacost"))
						: null;
				
				

				return new SkillElement.Builder(id, resourcelocation, displayinfo, advancementrewards, skillCapability, skillResourceCost);
			} catch (Exception ex) {
				DevilRpg.LOGGER.error("Ocurrió un error al deserializar", ex);
				return null;
			}
		}

		public static SkillElement.Builder deserializeMap(JsonObject json) {
			try {
				if (!json.has("id"))
					throw new JsonSyntaxException("SkillElement id cannot be empty");

				ResourceLocation id = new ResourceLocation(json.get("id").getAsString());

				ResourceLocation resourcelocation = json.has("parent")
						? new ResourceLocation(GsonHelper.getAsString(json, "parent"))
						: null;

				SkillDisplayInfo displayinfo = json.has("display")
						? SkillDisplayInfo.deserialize(GsonHelper.getAsJsonObject(json, "display"))
						: null;

				SkillElementRewards advancementrewards = json.has("rewards")
						? SkillElementRewards.deserializeRewards(GsonHelper.getAsJsonObject(json, "rewards"))
						: SkillElementRewards.EMPTY;

				SkillEnum skillCapability = json.has("capability")
						? SkillEnum.valueOf(GsonHelper.getAsJsonObject(json, "capability").getAsString())
						: SkillEnum.EMPTY;
				
				SkillResourceCost skillResourceCost = json.has("skillmanacost")
						?  SkillResourceCost.deserialize(GsonHelper.getAsJsonObject(json, "skillmanacost"))
						: null;

				return new SkillElement.Builder(id, resourcelocation, displayinfo, advancementrewards, skillCapability, skillResourceCost);
			} catch (Exception ex) {
				DevilRpg.LOGGER.error("Ocurrió un error al deserializar", ex);
				return null;
			}
		}

		public ResourceLocation getId() {
			return this.id;
		}
	}
}
