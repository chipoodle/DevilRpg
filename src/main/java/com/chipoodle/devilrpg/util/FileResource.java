package com.chipoodle.devilrpg.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.chipoodle.devilrpg.DevilRpg;
import com.google.common.collect.Lists;

import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;

public class FileResource {

	public final List<IResourcePack> resourcePacks = Lists.newArrayList();
	private final ResourcePackType type;

	public FileResource(ResourcePackType type, String namespace) {
		super();
		this.type = type;
	}

	public void addResourcePack(IResourcePack resourcePack) {
		this.resourcePacks.add(resourcePack);
	}

	public IResource getResource(ResourceLocation resourceLocationIn) throws IOException {
		this.checkResourcePath(resourceLocationIn);
		IResourcePack iresourcepack = null;
		ResourceLocation resourcelocation = resourceLocationIn;
		DevilRpg.LOGGER.info("getResource: "+ resourcelocation);

		for (int i = this.resourcePacks.size() - 1; i >= 0; --i) {
			IResourcePack iresourcepack1 = this.resourcePacks.get(i);
			if (iresourcepack == null && iresourcepack1.hasResource(this.type, resourcelocation)) {
				iresourcepack = iresourcepack1;
			}

			if (iresourcepack1.hasResource(this.type, resourceLocationIn)) {
				InputStream inputstream = null;
				if (iresourcepack != null) {
					inputstream = this.getInputStream(resourcelocation, iresourcepack);
				}

				return new SimpleResource(iresourcepack1.getName(), resourceLocationIn,
						this.getInputStream(resourceLocationIn, iresourcepack1), inputstream);
			}
		}

		throw new FileNotFoundException(resourceLocationIn.toString());
	}

	public boolean hasResource(ResourceLocation path) {
		if (!this.isValidLocation(path)) {
			return false;
		} else {
			for (int i = this.resourcePacks.size() - 1; i >= 0; --i) {
				IResourcePack iresourcepack = this.resourcePacks.get(i);
				if (iresourcepack.hasResource(this.type, path)) {
					return true;
				}
			}

			return false;
		}
	}

	protected InputStream getInputStream(ResourceLocation location, IResourcePack resourcePack) throws IOException {
		InputStream inputstream = resourcePack.getResource(this.type, location);
		return  inputstream;
	}

	static ResourceLocation getLocationMcmeta(ResourceLocation location) {
		return new ResourceLocation(location.getNamespace(), location.getPath() + ".mcmeta");
	}

	private void checkResourcePath(ResourceLocation location) throws IOException {
		DevilRpg.LOGGER.info("checkResourcePath: "+ location);
		if (!this.isValidLocation(location)) {
			throw new IOException("Invalid relative path to resource: " + location);
		}
	}

	private boolean isValidLocation(ResourceLocation p_219541_1_) {
		return !p_219541_1_.getPath().contains("..");
	}
}
