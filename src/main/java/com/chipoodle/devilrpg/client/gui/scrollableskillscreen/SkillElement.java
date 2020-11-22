package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.advancements.DisplayInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;

public class SkillElement {
	private final SkillElement parent;
	private final ScrollableSkillDisplayInfo display;
	private final SkillElementRewards rewards;
	private final ResourceLocation id;
	private final Set<SkillElement> children = Sets.newLinkedHashSet();
	private final ITextComponent displayText;

	public SkillElement(ResourceLocation id, @Nullable SkillElement parentIn,
			@Nullable ScrollableSkillDisplayInfo displayIn, SkillElementRewards rewardsIn) {
		this.id = id;
		this.display = displayIn;
		this.parent = parentIn;
		this.rewards = rewardsIn;
		if (parentIn != null) {
			parentIn.addChild(this);
		}

		if (displayIn == null) {
			this.displayText = new StringTextComponent(id.toString());
		} else {
			ITextComponent itextcomponent = displayIn.getTitle();
			TextFormatting textformatting = displayIn.getFrame().getFormat();
			ITextComponent itextcomponent1 = TextComponentUtils
					.func_240648_a_(itextcomponent.deepCopy(), Style.EMPTY.setFormatting(textformatting))
					.appendString("\n").append(displayIn.getDescription());
			ITextComponent itextcomponent2 = itextcomponent.deepCopy().modifyStyle((style) -> {
				return style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, itextcomponent1));
			});
			this.displayText = TextComponentUtils.wrapWithSquareBrackets(itextcomponent2).mergeStyle(textformatting);
		}

	}

	/**
	 * Creates a new advancement builder with the data from this advancement
	 */
	public SkillElement.Builder copy() {
		return new SkillElement.Builder(this.id, this.parent == null ? null : this.parent.getId(), this.display,
				this.rewards);
	}

	/**
	 * Get the {@code SkillElement} that is this {@code SkillElement}'s parent. This
	 * determines the tree structure that appears in the
	 * {@linkplain GuiScreenSkillElements GUI}.
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
	public ScrollableSkillDisplayInfo getDisplay() {
		return this.display;
	}

	public SkillElementRewards getRewards() {
		return this.rewards;
	}

	public String toString() {
		return "SimpleSkillElement{id=" + this.getId() + ", parent="
				+ (this.parent == null ? "null" : this.parent.getId()) + ", display=" + this.display + ", rewards="
				+ this.rewards + '}';
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
	 *         {@linkplain net.minecraft.advancements.FrameType#getFormat frame
	 *         type}, and hovering over it shows the
	 *         {@linkplain DisplayInfo#getDescription() description}.
	 */
	public ITextComponent getDisplayText() {
		return this.displayText;
	}

	public static class Builder {
		private ResourceLocation id;
		private ResourceLocation parentId;
		private SkillElement parent;
		private ScrollableSkillDisplayInfo display;
		private SkillElementRewards rewards = SkillElementRewards.EMPTY;

		private Builder(ResourceLocation id, @Nullable ResourceLocation parentIdIn,
				@Nullable ScrollableSkillDisplayInfo displayIn, SkillElementRewards rewardsIn) {
			this.parentId = parentIdIn;
			this.id = id;
			this.display = displayIn;
			this.rewards = rewardsIn;
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

		public SkillElement.Builder withDisplay(ItemStack stack, ITextComponent title, ITextComponent description,
				@Nullable ResourceLocation background, ScrollableSkillFrameType frame, boolean showToast,
				boolean announceToChat, boolean hidden) {
			return this.withDisplay(new ScrollableSkillDisplayInfo(stack, title, description, background, frame,
					showToast, announceToChat, hidden));
		}

		public SkillElement.Builder withDisplay(IItemProvider itemIn, ITextComponent title, ITextComponent description,
				@Nullable ResourceLocation background, ScrollableSkillFrameType frame, boolean showToast,
				boolean announceToChat, boolean hidden) {
			return this.withDisplay(new ScrollableSkillDisplayInfo(new ItemStack(itemIn.asItem()), title, description,
					background, frame, showToast, announceToChat, hidden));
		}

		public SkillElement.Builder withDisplay(ScrollableSkillDisplayInfo displayIn) {
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
					DevilRpg.LOGGER.info("|----Id: "+this.id+"  "+"ParentId: "+this.parentId);

				}

				return this.parent != null;
			}
		}

		public SkillElement build(ResourceLocation id) {
			if (!this.resolveParent((parentID) -> {
				return null;
			})) {
				throw new IllegalStateException("Tried to build incomplete advancement!");
			} else {

				return new SkillElement(id, this.parent, this.display, this.rewards);
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

		public void writeTo(PacketBuffer buf) {
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

				ResourceLocation id = new ResourceLocation(JSONUtils.getString(json, "id"));

				ResourceLocation resourcelocation = json.has("parent")
						? new ResourceLocation(JSONUtils.getString(json, "parent"))
						: null;

				ScrollableSkillDisplayInfo displayinfo = json.has("display")
						? ScrollableSkillDisplayInfo.deserialize(JSONUtils.getJsonObject(json, "display"))
						: null;

				SkillElementRewards advancementrewards = json.has("rewards")
						? SkillElementRewards.deserializeRewards(JSONUtils.getJsonObject(json, "rewards"))
						: SkillElementRewards.EMPTY;

				return new SkillElement.Builder(id, resourcelocation, displayinfo, advancementrewards);
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
						? new ResourceLocation(JSONUtils.getString(json, "parent"))
						: null;

				ScrollableSkillDisplayInfo displayinfo = json.has("display")
						? ScrollableSkillDisplayInfo.deserialize(JSONUtils.getJsonObject(json, "display"))
						: null;

				SkillElementRewards advancementrewards = json.has("rewards")
						? SkillElementRewards.deserializeRewards(JSONUtils.getJsonObject(json, "rewards"))
						: SkillElementRewards.EMPTY;

						
				return new SkillElement.Builder(id, resourcelocation, displayinfo, advancementrewards);
			} catch (Exception ex) {
				DevilRpg.LOGGER.error("Ocurrió un error al deserializar", ex);
				return null;
			}
		}

		public static SkillElement.Builder readFrom(PacketBuffer buf) {
			ResourceLocation id = buf.readBoolean() ? buf.readResourceLocation() : null;
			ResourceLocation resourcelocation = buf.readBoolean() ? buf.readResourceLocation() : null;
			ScrollableSkillDisplayInfo displayinfo = buf.readBoolean() ? ScrollableSkillDisplayInfo.read(buf) : null;
			return new SkillElement.Builder(id, resourcelocation, displayinfo, SkillElementRewards.EMPTY);
		}
		
		public ResourceLocation getId() {
			return this.id;
		}
	}
}
