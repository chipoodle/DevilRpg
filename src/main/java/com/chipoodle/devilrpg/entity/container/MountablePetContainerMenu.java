package com.chipoodle.devilrpg.entity.container;

import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.init.ModContainers;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;


public class MountablePetContainerMenu extends AbstractContainerMenu {

    private static final int DEFAULT_STORAGE = 15;   // 5 columnas x 3 filas = 15 slots de almacenamiento.

    private final Container horseContainer;
    private final Container armorContainer;
    private final AbstractHorse horse;
    private final int storageCapacity;

    /**
     * Constructor del CLIENTE (lo crea el MenuType con los datos extra del paquete: capacidad de
     * almacenamiento). El contenedor tiene siempre 5 columnas (=16 huecos) para que el fondo horse.png
     * se vea bien; la "mitad" se controla limitando cuantos slots estan ACTIVOS.
     */
    public MountablePetContainerMenu(int id, Inventory inventory, int storageCapacity) {
        super(ModContainers.MOUNTABLE_PET_MENU.get(), id);
        this.storageCapacity = Math.max(0, storageCapacity);
        this.horseContainer = new SimpleContainer(16);
        this.armorContainer = new SimpleContainer(1);
        this.horse = null;
        addAllSlots(this.horseContainer, this.armorContainer, null, inventory);
    }

    /** Constructor del SERVIDOR con el contenedor real (montura+almacenamiento) y la armadura real. */
    public MountablePetContainerMenu(int id, Inventory inventory, Container container, final AbstractHorse abstractHorse) {
        super(ModContainers.MOUNTABLE_PET_MENU.get(), id);
        this.storageCapacity = abstractHorse instanceof SoulBear sb ? sb.getMountStorageCapacity() : DEFAULT_STORAGE;
        this.horseContainer = container;
        this.armorContainer = abstractHorse != null ? abstractHorse.getBodyArmorAccess() : new SimpleContainer(1);
        this.horse = abstractHorse;
        container.startOpen(inventory.player);
        addAllSlots(this.horseContainer, this.armorContainer, abstractHorse, inventory);
    }

    /** Agrega montura (0), armadura (cont. de armadura), almacenamiento (1+) e inventario del jugador. */
    private void addAllSlots(Container container, Container armorContainer, AbstractHorse abstractHorse, Inventory inventory) {
        int columns = 5;

        // Slot de montura (index 0): SOLO LECTURA (se auto-equipa).
        this.addSlot(new Slot(container, 0, 8, 18) {
            @Override
            public boolean mayPlace(@NotNull ItemStack p_39677_) {
                return false;
            }

            @Override
            public boolean mayPickup(@NotNull Player player) {
                return false;
            }

            @Override
            public boolean isActive() {
                return abstractHorse == null || abstractHorse.isSaddleable();
            }
        });

        // Slot de armadura (cont. de armadura index 0): SOLO LECTURA (se auto-equipa).
        this.addSlot(new Slot(armorContainer, 0, 8, 36) {
            @Override
            public boolean mayPlace(@NotNull ItemStack itemStack) {
                return false;
            }

            @Override
            public boolean mayPickup(@NotNull Player player) {
                return false;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Almacenamiento (montura en index 0, items desde index 1) en cuadricula 5x3 (como el caballo).
        // Solo los primeros 'storageCapacity' slots estan ACTIVOS (mitad en nivel 1, completo en nivel 2);
        // el resto se muestra desactivado (no se puede colocar ni sacar).
        int storageSize = Math.max(0, container.getContainerSize() - 1);
        for (int k = 0; k < (storageSize + columns - 1) / columns; ++k) {
            for (int l = 0; l < columns && (k * columns + l) < storageSize; ++l) {
                final int position = k * columns + l;
                this.addSlot(new Slot(container, 1 + position, 80 + l * 18, 18 + k * 18) {
                    @Override
                    public boolean isActive() {
                        return storageCapacity > 0 && position < storageCapacity;
                    }
                });
            }
        }

        // Inventario del jugador.
        for (int i1 = 0; i1 < 3; ++i1) {
            for (int k1 = 0; k1 < 9; ++k1) {
                this.addSlot(new Slot(inventory, k1 + i1 * 9 + 9, 8 + k1 * 18, 102 + i1 * 18 + -18));
            }
        }
        for (int j1 = 0; j1 < 9; ++j1) {
            this.addSlot(new Slot(inventory, j1, 8 + j1 * 18, 142));
        }
    }

    public boolean stillValid(@NotNull Player player) {
        // En el cliente la instancia no tiene la entidad (horse==null); el servidor valida el alcance.
        if (horse == null) {
            return true;
        }
        return !this.horse.hasInventoryChanged(this.horseContainer) && this.horseContainer.stillValid(player) && this.horse.isAlive() && this.horse.distanceTo(player) < 8.0F;
    }

    /** Capacidad de almacenamiento (slots activos) segun el nivel; usado por la pantalla. */
    public int getStorageCapacity() {
        return this.storageCapacity;
    }

    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int id) {
        // Montura (0) y armadura (1) son de solo lectura: no se mueven por shift-click.
        if (id == 0 || id == 1)
            return ItemStack.EMPTY;

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(id);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int containerSize = this.horseContainer.getContainerSize();
            if (id < containerSize) {
                if (!this.moveItemStackTo(itemstack1, containerSize, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (containerSize <= 1 || !this.moveItemStackTo(itemstack1, 1, containerSize, false)) {
                int j = containerSize + 27;
                int k = j + 9;
                if (id >= j && id < k) {
                    if (!this.moveItemStackTo(itemstack1, containerSize, j, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (id >= containerSize && id < j) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, j, j, false)) {
                    return ItemStack.EMPTY;
                }
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    public void removed(@NotNull Player player) {
        super.removed(player);
        this.horseContainer.stopOpen(player);
    }

    @Override
    public void clicked(int i, int p_150401_, @NotNull ClickType clickType, @NotNull Player player) {
        // Montura (0) y armadura (1) de solo lectura: no se permite ninguna accion de click sobre ellas.
        if (i == 0 || i == 1)
            return;
        super.clicked(i, p_150401_, clickType, player);
    }
}
