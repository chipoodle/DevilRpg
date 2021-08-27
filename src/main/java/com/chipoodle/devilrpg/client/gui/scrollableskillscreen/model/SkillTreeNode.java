package com.chipoodle.devilrpg.client.gui.scrollableskillscreen.model;

import java.util.List;

import javax.annotation.Nullable;

import com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillElement;
import com.google.common.collect.Lists;


public class SkillTreeNode {
	private static final float LINE_WIDTH_FACTOR = 1.0f;//0.50F;
	private final SkillElement rootSkillElement;
	private final SkillTreeNode parent;
	private final SkillTreeNode sibling;
	private final int index;
	private final List<SkillTreeNode> children = Lists.newArrayList();
	private SkillTreeNode ancestor;
	private SkillTreeNode thread;
	private int x;
	private float y;
	private float mod;
	private float change;
	private float shift;

	public SkillTreeNode(SkillElement rootSkillelement, @Nullable SkillTreeNode parentIn, @Nullable SkillTreeNode siblingIn, int indexIn, int xIn) {
	      if (rootSkillelement.getDisplay() == null) {
	         throw new IllegalArgumentException("Can't position an invisible advancement!");
	      } else {
	         this.rootSkillElement = rootSkillelement;
	         this.parent = parentIn;
	         this.sibling = siblingIn;
	         this.index = indexIn;
	         this.ancestor = this;
	         this.x = xIn;
	         this.y = -1.0F;
	         SkillTreeNode advancementtreenode = null;

	         for(SkillElement skillNode : rootSkillelement.getChildren()) {
	            advancementtreenode = this.buildSubTree(skillNode, advancementtreenode);
	         }

	      }
	   }

	@Nullable
	private SkillTreeNode buildSubTree(SkillElement advancementIn, @Nullable SkillTreeNode previous) {
		if (advancementIn.getDisplay() != null) {
			previous = new SkillTreeNode(advancementIn, this, previous, this.children.size() + 1, this.x + 1);
			this.children.add(previous);
		} else {
			for (SkillElement advancement : advancementIn.getChildren()) {
				previous = this.buildSubTree(advancement, previous);
			}
		}

		return previous;
	}

	private void firstWalk() {
		if (this.children.isEmpty()) {
			if (this.sibling != null) {
				this.y = this.sibling.y + LINE_WIDTH_FACTOR;
			} else {
				this.y = 0.0F;
			}

		} else {
			SkillTreeNode skillTreenode = null;

			for (SkillTreeNode skillTreenode1 : this.children) {
				skillTreenode1.firstWalk();
				skillTreenode = skillTreenode1
						.apportion(skillTreenode == null ? skillTreenode1 : skillTreenode);
			}

			this.executeShifts();
			float f = ((this.children.get(0)).y + (this.children.get(this.children.size() - 1)).y) / 2.0F;
			if (this.sibling != null) {
				this.y = this.sibling.y + 1.0F;
				this.mod = this.y - f;
			} else {
				this.y = f;
			}

		}
	}

	private float secondWalk(float offsetY, int columnX, float subtreeTopY) {
		this.y += offsetY;
		this.x = columnX;
		if (this.y < subtreeTopY) {
			subtreeTopY = this.y;
		}

		for (SkillTreeNode advancementtreenode : this.children) {
			subtreeTopY = advancementtreenode.secondWalk(offsetY + this.mod, columnX + 1, subtreeTopY);
		}

		return subtreeTopY;
	}

	private void thirdWalk(float yIn) {
		this.y += yIn;

		for (SkillTreeNode advancementtreenode : this.children) {
			advancementtreenode.thirdWalk(yIn);
		}

	}

	private void executeShifts() {
		float f = 0.0F;
		float f1 = 0.0F;

		for (int i = this.children.size() - 1; i >= 0; --i) {
			SkillTreeNode advancementtreenode = this.children.get(i);
			advancementtreenode.y += f;
			advancementtreenode.mod += f;
			f1 += advancementtreenode.change;
			f += advancementtreenode.shift + f1;
		}

	}

	@Nullable
	private SkillTreeNode getFirstChild() {
		if (this.thread != null) {
			return this.thread;
		} else {
			return !this.children.isEmpty() ? this.children.get(0) : null;
		}
	}

	@Nullable
	private SkillTreeNode getLastChild() {
		if (this.thread != null) {
			return this.thread;
		} else {
			return !this.children.isEmpty() ? this.children.get(this.children.size() - 1) : null;
		}
	}

	private SkillTreeNode apportion(SkillTreeNode nodeIn) {
		if (this.sibling == null) {
			return nodeIn;
		} else {
			SkillTreeNode advancementtreenode = this;
			SkillTreeNode advancementtreenode1 = this;
			SkillTreeNode advancementtreenode2 = this.sibling;
			SkillTreeNode advancementtreenode3 = this.parent.children.get(0);
			float f = this.mod;
			float f1 = this.mod;
			float f2 = advancementtreenode2.mod;

			float f3;
			for (f3 = advancementtreenode3.mod; advancementtreenode2.getLastChild() != null
					&& advancementtreenode.getFirstChild() != null; f1 += advancementtreenode1.mod) {
				advancementtreenode2 = advancementtreenode2.getLastChild();
				advancementtreenode = advancementtreenode.getFirstChild();
				advancementtreenode3 = advancementtreenode3.getFirstChild();
				advancementtreenode1 = advancementtreenode1.getLastChild();
				advancementtreenode1.ancestor = this;
				float f4 = advancementtreenode2.y + f2 - (advancementtreenode.y + f) + 1.0F;
				if (f4 > 0.0F) {
					advancementtreenode2.getAncestor(this, nodeIn).moveSubtree(this, f4);
					f += f4;
					f1 += f4;
				}

				f2 += advancementtreenode2.mod;
				f += advancementtreenode.mod;
				f3 += advancementtreenode3.mod;
			}

			if (advancementtreenode2.getLastChild() != null && advancementtreenode1.getLastChild() == null) {
				advancementtreenode1.thread = advancementtreenode2.getLastChild();
				advancementtreenode1.mod += f2 - f1;
			} else {
				if (advancementtreenode.getFirstChild() != null && advancementtreenode3.getFirstChild() == null) {
					advancementtreenode3.thread = advancementtreenode.getFirstChild();
					advancementtreenode3.mod += f - f3;
				}

				nodeIn = this;
			}

			return nodeIn;
		}
	}

	private void moveSubtree(SkillTreeNode nodeIn, float shift) {
		float f = (float) (nodeIn.index - this.index);
		if (f != 0.0F) {
			nodeIn.change -= shift / f;
			this.change += shift / f;
		}

		nodeIn.shift += shift;
		nodeIn.y += shift;
		nodeIn.mod += shift;
	}

	private SkillTreeNode getAncestor(SkillTreeNode self, SkillTreeNode other) {
		return this.ancestor != null && self.parent.children.contains(this.ancestor) ? this.ancestor : other;
	}

	private void updatePosition() {
		if (this.rootSkillElement.getDisplay() != null) {
			this.rootSkillElement.getDisplay().setPosition(this.x, this.y);
		}

		if (!this.children.isEmpty()) {
			for (SkillTreeNode advancementtreenode : this.children) {
				advancementtreenode.updatePosition();
			}
		}

	}

	public static void layout(SkillElement root) {
		if (root.getDisplay() == null) {
			throw new IllegalArgumentException("Can't position children of an invisible root!");
		} else {
			SkillTreeNode advancementtreenode = new SkillTreeNode(root, (SkillTreeNode) null,
					(SkillTreeNode) null, 1, 0);
			advancementtreenode.firstWalk();
			float f = advancementtreenode.secondWalk(0.0F, 0, advancementtreenode.y);
			if (f < 0.0F) {
				advancementtreenode.thirdWalk(-f);
			}

			advancementtreenode.updatePosition();
		}
	}
}
