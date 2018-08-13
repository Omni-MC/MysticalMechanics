package mysticalmechanics.tileentity;

import mysticalmechanics.api.DefaultMechCapability;
import mysticalmechanics.api.IGearBehavior;
import mysticalmechanics.api.IGearbox;
import mysticalmechanics.api.MysticalMechanicsAPI;
import mysticalmechanics.block.BlockGearbox;
import mysticalmechanics.handler.RegistryHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TileEntityGearbox extends TileEntity implements ITickable, IGearbox {
    EnumFacing from = null;
    public int connections = 0;
    public ItemStack[] gears = new ItemStack[]{
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
    };
    public double angle, lastAngle;

    public DefaultMechCapability capability = new DefaultMechCapability() {
        @Override
        public void onPowerChange() {
            TileEntityGearbox box = TileEntityGearbox.this;
            box.updateNeighbors();
            box.markDirty();
        }

        @Override
        public double getPower(EnumFacing from) {
            ItemStack gearStack = getGear(from);
            if (from != null && gearStack.isEmpty()) {
                return 0;
            }
            IGearBehavior behavior = MysticalMechanicsAPI.IMPL.getGearBehavior(gearStack);
            double power;

            if (from == TileEntityGearbox.this.from || from == null)
                power = super.getPower(from);
            else
                power = getPower(TileEntityGearbox.this.from) / ((double) (Math.max(1, getConnections())));

            return behavior.transformPower(TileEntityGearbox.this, from, gearStack, power);
        }

        @Override
        public void setPower(double value, EnumFacing from) {
            ItemStack gearStack = getGear(from);
            if (from != null && gearStack.isEmpty()) {
                return;
            }
            if (from == TileEntityGearbox.this.from || from == null) {
                super.setPower(value, from);
            }
            if (value != getPower(null)) {
                onPowerChange();
            }
        }
    };

    public void updateNeighbors() {
        IBlockState state = world.getBlockState(getPos());
        if (state.getBlock() instanceof BlockGearbox) {
            from = state.getValue(BlockGearbox.facing);
            TileEntity t = world.getTileEntity(getPos().offset(from));
            if (t != null && t.hasCapability(MysticalMechanicsAPI.MECH_CAPABILITY, from.getOpposite()) && !getGear(from).isEmpty()) {
                capability.setPower(t.getCapability(MysticalMechanicsAPI.MECH_CAPABILITY, from.getOpposite()).getPower(from.getOpposite()), null);
            } else
                capability.setPower(0, null);
        }
        connections = 0;
        List<EnumFacing> toUpdate = new ArrayList<>();
        for (EnumFacing f : EnumFacing.values()) {
            if (f != null && f != from) {
                TileEntity t = world.getTileEntity(getPos().offset(f));
                if (t != null && t.hasCapability(MysticalMechanicsAPI.MECH_CAPABILITY, f.getOpposite())) {
                    if (!getGear(f).isEmpty())
                        connections++;
                    toUpdate.add(f);
                }
            }
        }
        for (EnumFacing f : toUpdate) {
            BlockPos p = getPos().offset(f);
            TileEntity t = world.getTileEntity(p);
            t.getCapability(MysticalMechanicsAPI.MECH_CAPABILITY, f.getOpposite()).setPower(capability.getPower(f), f.getOpposite());
        }
        markDirty();
    }

    public TileEntityGearbox() {
        super();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setDouble("mech_power", capability.power);
        if (from != null) {
            tag.setInteger("from", from.getIndex());
        }
        for (int i = 0; i < 6; i++) {
            tag.setTag("gear" + i, gears[i].writeToNBT(new NBTTagCompound()));
        }
        tag.setInteger("connections", connections);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("mech_power")) {
            capability.power = tag.getDouble("mech_power");
        }
        if (tag.hasKey("from")) {
            from = EnumFacing.getFront(tag.getInteger("from"));
        }
        for (int i = 0; i < 6; i++) {
            gears[i] = new ItemStack(tag.getCompoundTag("gear" + i));
        }
        connections = tag.getInteger("connections");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == MysticalMechanicsAPI.MECH_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == MysticalMechanicsAPI.MECH_CAPABILITY) {
            return (T) this.capability;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void attachGear(EnumFacing facing, ItemStack stack) {
        if (facing == null)
            return;
        gears[facing.getIndex()] = stack;
        markDirty();
    }

    @Override
    public ItemStack detachGear(EnumFacing facing) {
        if (facing == null)
            return ItemStack.EMPTY;
        int index = facing.getIndex();
        ItemStack gear = gears[index];
        gears[index] = ItemStack.EMPTY;
        markDirty();
        return gear;
    }

    public ItemStack getGear(EnumFacing facing) {
        if (facing == null)
            return ItemStack.EMPTY;
        return gears[facing.getIndex()];
    }

    @Override
    public boolean canAttachGear(EnumFacing facing, ItemStack stack) {
        return true;
    }

    @Override
    public int getConnections() {
        return connections;
    }

    public boolean activate(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                            EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && canAttachGear(side,heldItem)) {
            if (getGear(side).isEmpty() && MysticalMechanicsAPI.IMPL.isValidGear(heldItem)) {
                ItemStack gear = heldItem.copy();
                gear.setCount(1);
                attachGear(side,gear);
                heldItem.shrink(1);
                if (heldItem.isEmpty()) {
                    player.setHeldItem(hand, ItemStack.EMPTY);
                }
                capability.onPowerChange();
                return true;
            }
        } else if (!getGear(side).isEmpty()) {
            ItemStack gear = detachGear(side);
            if (!world.isRemote) {
                world.spawnEntity(new EntityItem(world, player.posX, player.posY + player.height / 2.0f, player.posZ, gear));
            }
            capability.onPowerChange();
            return true;
        }
        return false;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        syncTE();
    }

    public void syncTE() {
        IBlockState state = this.world.getBlockState(this.pos);
        this.world.notifyBlockUpdate(pos, state, state, 3);
    }

    public void breakBlock(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        for (int i = 0; i < 6; i++) {
            if (!world.isRemote) {
                world.spawnEntity(new EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gears[i]));
            }
            gears[i] = ItemStack.EMPTY;
        }
        capability.setPower(0f, null);
        updateNeighbors();
    }

    @Override
    public void update() {
        if (world.isRemote) {
            lastAngle = angle;
            angle += capability.getPower(null);
            for(EnumFacing facing : EnumFacing.VALUES) {
                ItemStack gear = getGear(facing);
                IGearBehavior behavior = MysticalMechanicsAPI.IMPL.getGearBehavior(gear);
                behavior.visualUpdate(this,facing,gear);
            }
        }
    }
}