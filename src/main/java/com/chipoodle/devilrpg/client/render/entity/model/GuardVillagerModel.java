package com.chipoodle.devilrpg.client.render.entity.model;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * Modelo del <b>guardia de la aldea</b>: el cuerpo del <b>jugador</b> (humanoide) con la <b>cabeza de aldeano</b> (su
 * narizota, añadida como una caja más de la cabeza). Lo pidió así el jugador: *"puedes usar la del jugador pero con
 * cabeza de aldeano"*.
 * <p>
 * <b>¿Por qué un modelo nuevo y no el del aldeano?</b> Porque el del aldeano no sirve para enseñar el equipo (medido
 * en las fuentes de 1.21): {@code VillagerModel} <b>no</b> es {@code HumanoidModel} ni {@code ArmedModel}, así que
 * {@code HumanoidArmorLayer} (exige {@code M extends HumanoidModel}) e {@code ItemInHandLayer} (exige
 * {@code ArmedModel}) <b>no se le pueden enchufar</b>, y {@code VillagerRenderer} no trae ninguna capa de armadura ni
 * de objeto en mano (solo cabeza, profesión y brazos cruzados). Este modelo sí es un {@link HumanoidModel}, así que
 * con las <b>capas de vanilla</b> se le ve la armadura, la espada, el escudo y el arco.
 * <p>
 * Se dibuja <b>solo a los guardias</b>: el renderer que lo usa delega en el {@code VillagerRenderer} de vanilla para
 * cualquier otro aldeano (ver {@code GuardVillagerRenderer}), así que a los aldeanos normales no les cambia nada.
 */
public class GuardVillagerModel extends HumanoidModel<Villager> {

    /** Capa del modelo (se registra en {@code ClientModRegistryEventSubscriber.onRegisterLayers}). */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "village_guard"), "main");

    public GuardVillagerModel(ModelPart root) {
        super(root);
    }

    /**
     * Cuerpo del guardia: la malla del jugador (brazos y piernas humanoides, que es lo que hace falta para que el
     * juego le ponga la armadura y el arma con sus capas de siempre) más la <b>nariz</b> del aldeano.
     * <p>
     * La nariz va en la esquina libre del mapa de texturas del jugador (<b>0,32</b>): su caja de 2x3x2 ocupa 8x5
     * píxeles y esa zona no la usa el layout del jugador, así que no pisa ninguna cara del cuerpo.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition head = mesh.getRoot().getChild("head");
        head.addOrReplaceChild("nose",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -1.5F, -5.0F, 2.0F, 3.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
