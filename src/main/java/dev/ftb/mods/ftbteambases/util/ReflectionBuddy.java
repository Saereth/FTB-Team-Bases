package dev.ftb.mods.ftbteambases.util;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess.ImmutableRegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Joseph Bettendorff a.k.a. "Commoble"
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class ReflectionBuddy
{	
	/**
	 * Gets a getter-like object for a reflective field. Only to be used for obfuscatable vanilla minecraft fields
	 * @param <FIELDHOLDER> The type of the object containing the field
	 * @param <FIELDTYPE> The type of the values the field would contain
	 * @param fieldHolderClass The class of the object containing the field
	 * @param fieldName The SRG (intermediary-obfuscated) name of the field
	 * @return A getter for the field
	 */
	public static <FIELDHOLDER,FIELDTYPE> Function<FIELDHOLDER,FIELDTYPE> getInstanceFieldGetter(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
	{
		Field field = ObfuscationReflectionHelper.findField(fieldHolderClass, fieldName);
		return getInstanceFieldGetter(field);
	}
	
	public static <FIELDHOLDER,FIELDTYPE> MutableInstanceField<FIELDHOLDER,FIELDTYPE> getInstanceField(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
	{
		return new MutableInstanceField<>(fieldHolderClass, fieldName);
	}

	@SuppressWarnings("unchecked") // throws ClassCastException if the types are wrong, the returned function can also throw RuntimeException
	private static <FIELDHOLDER,FIELDTYPE> Function<FIELDHOLDER,FIELDTYPE> getInstanceFieldGetter(Field field)
	{
		return instance -> {
			try
			{
				return (FIELDTYPE)(field.get(instance));
			}
			catch (IllegalArgumentException | IllegalAccessException e)
			{
				throw new RuntimeException(e);
			}
		};
	}
		
	public static class MutableInstanceField<FIELDHOLDER, FIELDTYPE>
	{
		private final Function<FIELDHOLDER,FIELDTYPE> getter;
		private final BiConsumer<FIELDHOLDER,FIELDTYPE> setter;
		
		private MutableInstanceField(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
		{
			Field field = ObfuscationReflectionHelper.findField(fieldHolderClass, fieldName);
			this.getter = getInstanceFieldGetter(field);
			this.setter = getInstanceFieldSetter(field);
		}
		
		/**
		 * Returns the current value of the field in a given instance
		 * @param instance The object containing the instance field to get the value from
		 * @return The value in that field
		 */
		public FIELDTYPE get(FIELDHOLDER instance)
		{
			return this.getter.apply(instance);
		}
		
		/**
		 * Sets an object's field to the given value
		 * @param instance The object containing the instance field to set the value in
		 * @param value The value to set
		 */
		public void set(FIELDHOLDER instance, FIELDTYPE value)
		{
			this.setter.accept(instance, value);
		}
		
		// the returned function throws RuntimeException if the types are wrong
		private static <FIELDHOLDER, FIELDTYPE> BiConsumer<FIELDHOLDER, FIELDTYPE> getInstanceFieldSetter(Field field)
		{
			return (instance,value) -> {
				try
				{
					field.set(instance, value);
				}
				catch (IllegalArgumentException | IllegalAccessException e)
				{
					throw new RuntimeException(e);
				}
			};
		}
	}

	/** fields in MinecraftServer needed for registering dimensions **/
	public static class MinecraftServerAccess
	{
		// we need to read some private fields in MinecraftServer
		// we can use Access Transformers, Accessor Mixins, or ObfuscationReflectionHelper to get at these
		// we'll use ORH here as ATs and Mixins seem to be causing headaches for dependant mods lately
			// it also lets us define the private-field-getting-shenanigans in the same class we're using them
			// it also doesn't need any extra resources or buildscript stuff, which makes this example simpler to describe
		public static final Function<MinecraftServer, ChunkProgressListenerFactory> progressListenerFactory =
			getInstanceFieldGetter(MinecraftServer.class, "progressListenerFactory");
		public static final Function<MinecraftServer, Executor> executor =
			getInstanceFieldGetter(MinecraftServer.class, "executor");
		public static final Function<MinecraftServer, LevelStorageAccess> storageSource =
			getInstanceFieldGetter(MinecraftServer.class, "storageSource");
		public static final Function<MinecraftServer, LayeredRegistryAccess<RegistryLayer>> registries =
			getInstanceFieldGetter(MinecraftServer.class, "registries");
	}

	public static class WorldBorderAccess
	{
		public static final Function<WorldBorder, List<BorderChangeListener>> listeners =
			getInstanceFieldGetter(WorldBorder.class, "listeners");
	}

	public static class DelegateBorderChangeListenerAccess
	{
		public static final Function<BorderChangeListener.DelegateBorderChangeListener, WorldBorder> worldBorder =
			getInstanceFieldGetter(BorderChangeListener.DelegateBorderChangeListener.class, "worldBorder");
	}
	
	public static class LayeredRegistryAccessAccess
	{
		@SuppressWarnings("rawtypes")
		public static final MutableInstanceField<LayeredRegistryAccess, List<RegistryAccess.Frozen>> values =
			getInstanceField(LayeredRegistryAccess.class, "values");
		@SuppressWarnings("rawtypes")
		public static final Function<LayeredRegistryAccess, RegistryAccess.Frozen> composite =
			getInstanceFieldGetter(LayeredRegistryAccess.class, "composite");
	}
	
	public static class ImmutableRegistryAccessAccess
	{
		public static final MutableInstanceField<ImmutableRegistryAccess, Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>>> registries =
			getInstanceField(ImmutableRegistryAccess.class, "registries");
	}
}