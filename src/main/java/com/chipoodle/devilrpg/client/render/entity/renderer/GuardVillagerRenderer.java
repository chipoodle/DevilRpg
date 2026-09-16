package com.chipoodle.devilrpg.client.render.entity.renderer;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.client.render.entity.model.GuardVillagerModel;
import com.chipoodle.devilrpg.entity.goal.VillagerGuardGoal;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * Renderer del <b>guardia de la aldea</b>: el único que hace que <b>se le vea el equipo</b>.
 * <p>
 * El aldeano de vanilla no puede enseñar armadura ni lo que lleva en la mano ({@code VillagerRenderer} no trae esas
 * capas y {@code VillagerModel} no es {@code HumanoidModel} ni {@code ArmedModel}), así que los guardias se dibujan
 * con {@link GuardVillagerModel} (cuerpo de jugador + cabeza de aldeano) y, con él, se le pueden poner las <b>capas
 * de vanilla</b>:
 * <ul>
 *   <li>{@link HumanoidArmorLayer}: casco, peto, grebas y botas.</li>
 *   <li>{@link ItemInHandLayer}: la espada, el escudo, el arco y las flechas en la mano.</li>
 * </ul>
 * Al aldeano que <b>no</b> es guardia se le sigue dibujando con el {@link VillagerRenderer} de vanilla (se guarda uno
 * dentro y se delega), así que su ropa de profesión y de bioma no cambia: este renderer solo cambia a la milicia.
 * <p>
 * Se registra para {@code EntityType.VILLAGER} en {@code ClientModRegistryEventSubscriber.onRegisterRenderers}
 * ({@code EntityRenderersEvent.RegisterRenderers.registerEntityRenderer} llama a {@code EntityRenderers.register}, que
 * <b>sustituye</b> el renderer de vanilla).
 */
public class GuardVillagerRenderer extends MobRenderer<Villager, GuardVillagerModel> {

    /** Textura del espadachín (uniforme de acero). */
    private static final ResourceLocation ESPADACHIN = textura("village_guard_swordsman");
    /** Textura del arquero (uniforme verde de monte). */
    private static final ResourceLocation ARQUERO = textura("village_guard_archer");

    /** Renderer de vanilla: los aldeanos que NO son guardia se dibujan con él, igual que siempre. */
    private final VillagerRenderer aldeanoNormal;

    public GuardVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardVillagerModel(context.bakeLayer(GuardVillagerModel.LAYER_LOCATION)), 0.5F);
        this.aldeanoNormal = new VillagerRenderer(context);
        // LAS DOS CAPAS QUE HACEN QUE SE LE VEA EL EQUIPO (son las de vanilla; el modelo del aldeano no las admitía).
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(Villager villager, float yaw, float parcial, PoseStack pose, MultiBufferSource buffer, int luz) {
        if (!VillagerGuardGoal.esGuardia(villager)) {
            // Aldeano de siempre: lo dibuja el renderer de vanilla. La marca se lee del aldeano (viaja con él), así que
            // al alistarse o dejar la guardia el cambio de modelo es inmediato.
            this.aldeanoNormal.render(villager, yaw, parcial, pose, buffer, luz);
            return;
        }
        super.render(villager, yaw, parcial, pose, buffer, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(Villager villager) {
        return VillagerGuardGoal.tipoDe(villager) == VillagerGuardGoal.ARQUERO ? ARQUERO : ESPADACHIN;
    }

    private static ResourceLocation textura(String nombre) {
        return ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "textures/entity/guard/" + nombre + ".png");
    }
}
