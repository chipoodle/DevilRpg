/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.eventsubscriber.client;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.util.EventUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * Subscribe to events from the FORGE EventBus that should be handled on the
 * PHYSICAL CLIENT side in this class
 *
 * @author Chipoodle
 */
@EventBusSubscriber(modid = DevilRpg.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeCustomCameraEventSubscriber {
    private static final Class<?>[] types = {double.class, double.class, double.class};
    private static boolean firstPersonView = false;
    private static boolean firstTimeShapeshifted = true;
    private static CameraType originalCameraType;
    private static Method cameraMoveMethod = null;
    private static boolean marked = false;

    /**
     * @param event
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Player player = Minecraft.getInstance().player;
        BiConsumer<ViewportEvent.ComputeCameraAngles, LazyOptional<PlayerAuxiliaryCapabilityInterface>> c = (eve, auxiliar) -> {
        };

        if (EventUtils.onWerewolfTransformation(player, c, event)) {
            initializeCameraMoveMethod(event);
            firstTimeShapeshifted = true;

            CameraType cameraType = Minecraft.getInstance().options.getCameraType();

            switch (cameraType) {
                case FIRST_PERSON -> handleFirstPerson(cameraType);
                case THIRD_PERSON_BACK -> {
                    if (firstPersonView) {
                        simulateFirstPersonView(event, player);
                    } else {
                        handleThirdPersonBack(event);
                    }
                }
                case THIRD_PERSON_FRONT -> handleThirdPersonFront();
            }
        } else {
            restoreCamera();

        }
    }

    private static void handleFirstPerson(CameraType cameraType) {
        firstPersonView = true;
        marked = true;
        Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        originalCameraType = cameraType;
    }

    private static void handleThirdPersonFront() {
        if (firstPersonView) {
            firstPersonView = false;
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            originalCameraType = CameraType.THIRD_PERSON_BACK;
        } else {
            originalCameraType = CameraType.THIRD_PERSON_FRONT;
            if (marked) {
                Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                originalCameraType = CameraType.FIRST_PERSON;
            }
        }
    }

    private static void handleThirdPersonBack(ViewportEvent.ComputeCameraAngles event) throws IllegalAccessException, InvocationTargetException {
        cameraMoveMethod.invoke(event.getCamera(), 0.0D, 1.1D, 0.0D);
        marked = false;
        originalCameraType = CameraType.THIRD_PERSON_BACK;
    }

    private static void simulateFirstPersonView(ViewportEvent.ComputeCameraAngles event, Player player) throws IllegalAccessException, InvocationTargetException {
        // Establecemos la posición de la cámara para simular la vista de primera persona
        cameraMoveMethod.invoke(event.getCamera(), 4.80D, 0.20D, 0.0D);
        // Ajustamos la rotación de la cámara para coincidir con la vista de primera persona
        event.getCamera().setAnglesInternal(player.getYRot(), player.getXRot());
        originalCameraType = CameraType.FIRST_PERSON;
    }

    private static void restoreCamera() {
        if (originalCameraType == null || !firstTimeShapeshifted) {
            originalCameraType = Minecraft.getInstance().options.getCameraType();
        }
        if (firstTimeShapeshifted) {
            Minecraft.getInstance().options.setCameraType(originalCameraType);
            firstTimeShapeshifted = false;
            firstPersonView = false;
            marked = false;
            originalCameraType = null;
        }
    }

    private static void initializeCameraMoveMethod(ViewportEvent.ComputeCameraAngles event) throws NoSuchMethodException {
        if (cameraMoveMethod == null) {
            cameraMoveMethod = event.getCamera().getClass().getDeclaredMethod("move", types);
            cameraMoveMethod.setAccessible(true);
        }
    }
}
