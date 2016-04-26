package lordfokas.stargatetech2.lib.tileentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;

import cofh.api.tileentity.IReconfigurableFacing;
import cofh.api.tileentity.IReconfigurableSides;
import cofh.api.tileentity.IRedstoneControl;
import gnu.trove.list.array.TIntArrayList;
import lordfokas.stargatetech2.api.bus.IBusInterface;
import lordfokas.stargatetech2.lib.packet.PacketMachineConfiguration;
import lordfokas.stargatetech2.lib.packet.PacketMachineRedstone;
import lordfokas.stargatetech2.lib.tileentity.FakeInterfaces.IFakeEnergyHandler;
import lordfokas.stargatetech2.lib.tileentity.FakeInterfaces.IFakeFluidHandler;
import lordfokas.stargatetech2.lib.tileentity.FakeInterfaces.IFakeSidedInventory;
import lordfokas.stargatetech2.lib.tileentity.FakeInterfaces.IFakeSyncBusDevice;
import lordfokas.stargatetech2.lib.tileentity.ITileContext.Client;
import lordfokas.stargatetech2.lib.tileentity.ITileContext.Server;
import lordfokas.stargatetech2.lib.tileentity.component.IAccessibleTileComponent;
import lordfokas.stargatetech2.lib.tileentity.component.IComponentProvider;
import lordfokas.stargatetech2.lib.tileentity.component.IComponentRegistrar;
import lordfokas.stargatetech2.lib.tileentity.component.ITileComponent;
import lordfokas.stargatetech2.lib.tileentity.component.access.IBusComponent;
import lordfokas.stargatetech2.lib.tileentity.component.access.ICapacitorComponent;
import lordfokas.stargatetech2.lib.tileentity.component.access.IInventoryComponent;
import lordfokas.stargatetech2.lib.tileentity.component.access.ITankComponent;
import lordfokas.stargatetech2.lib.tileentity.faces.Face;
import lordfokas.stargatetech2.lib.tileentity.faces.FaceColor;
import lordfokas.stargatetech2.lib.tileentity.faces.IFacingAware;
import lordfokas.stargatetech2.lib.tileentity.faces.IFacingProvider;
import lordfokas.stargatetech2.modules.automation.ISyncBusDevice;
import lordfokas.stargatetech2.reference.TextureReference;
import lordfokas.stargatetech2.util.Helper;
import lordfokas.stargatetech2.util.IconRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

/**
 * An advanced TileEntity. Supplies services like automatic rotation handling,
 * side colors, components, among others.<br>
 * <br>
 * If a Context implements {@link IFacingAware} it will be provided with access
 * to this TileEntity's facing data.<br>
 * <br>
 * If a Context implements {@link IComponentProvider} it will be allowed to register
 * components to this machine. Components should implement {@link ITileComponent}.<br>
 * <br>
 * If a Context implements {@link IRedstoneAware} it will be notified of whether or
 * not it should run based on the TE's redstone control mode.<br>
 * <br>
 * Components that implement {@link IAccessibleTileComponent} will be exposed to
 * the outside of the TileEntity.<br>
 * Components that implement {@link IFacingAware} will be allowed to read this
 * TileEntity's facing data.<br>
 * Components that implement {@link ISyncedGUI.Flow} will automatically be synced.
 *
 * @param <C> The type {@link BaseTileEntity#getClientContext()} should return.
 * @param <S> The type {@link BaseTileEntity#getServerContext()} should return.
 * 
 * @author LordFokas
 */
public class TileEntityMachine<C extends Client, S extends Server> extends BaseTileEntity<C, S>
implements IReconfigurableSides, IReconfigurableFacing, IFacingProvider, IComponentRegistrar,
IRedstoneControl,

// fake interfaces so that the compiler helps us. *************************************
IFakeFluidHandler, IFakeSidedInventory, IFakeEnergyHandler, IFakeSyncBusDevice{    //**
// ************************************************************************************	
	
	private static final int COMPONENT_KEYS = 100;
	private static final Class[] INTERFACES = new Class[]{
		IBusComponent.class, ICapacitorComponent.class, IInventoryComponent.class, ITankComponent.class
	};
	
	private boolean isComponentRegistrationAllowed = false;
	private EnumMap<Face, FaceWrapper> faces = new EnumMap(Face.class);
	private Face[] faceMap = new Face[6];
	private EnumFacing facing;
	private ControlMode redstoneControl = ControlMode.DISABLED;
	private boolean redstonePower = false;
	private IRedstoneAware rsContext;
	
	private ArrayList<ITileComponent> allComponents = new ArrayList();
	private ArrayList<ISyncedGUI.Flow> syncComponents = new ArrayList();
	private int[] syncKeys; // Cached value set for those ^
	private HashMap<Class, ArrayList<? extends IAccessibleTileComponent>> sidedComponents = new HashMap();
	
	public TileEntityMachine(Class<? extends C> client, Class<? extends S> server, FaceColor ... colors) {
		super(client, server);
		facing = EnumFacing.SOUTH;
		setMap(EnumFacing.UP, Face.TOP);
		setMap(EnumFacing.DOWN, Face.BOTTOM);
		remapSides(false); // don't update the clients; we may even be on the client side!
		for(Face face : faceMap){
			faces.put(face, new FaceWrapper(colors));
		}
		if(context instanceof IFacingAware){
			((IFacingAware)context).setProvider(this);
		}
		if(context instanceof IRedstoneAware){
			rsContext = (IRedstoneAware) context;
		}
		for(Class iface : INTERFACES){
			sidedComponents.put(iface, new ArrayList());
		}
		isComponentRegistrationAllowed = true;
		if(context instanceof IComponentProvider){
			((IComponentProvider)context).registerComponents(this);
		}
		isComponentRegistrationAllowed = false;
		cacheSyncValueArray();
	}
	
	@Override
	public void registerComponent(ITileComponent component) {
		if(!isComponentRegistrationAllowed)
			throw new RuntimeException("ITileComponent registration CANNOT be delayed. Respect the f**king API!");
		allComponents.add(component);
		if(component instanceof IFacingAware){
			((IFacingAware)component).setProvider(this);
		}
		if(component instanceof IBusComponent){ // TODO: find a better way to work around this
			((IBusComponent)component).setBusDevice((ISyncBusDevice)this);
		}
		if(component instanceof ISyncedGUI.Flow){
			syncComponents.add((ISyncedGUI.Flow) component);
		}
		if(component instanceof IAccessibleTileComponent){
			Class cls = component.getClass();
			for(Class iface : INTERFACES){
				if(iface.isAssignableFrom(cls)){
					((ArrayList<ITileComponent>) sidedComponents.get(iface)).add(component);
					return;
				}
			}
		}
	}
	
	// ##########################################################
	// Reconfigurable Facing
	
	@Override
	public int getFacing() {
		return facing.ordinal();
	}

	@Override
	public boolean allowYAxisFacing() {
		return false;
	}

	@Override
	public boolean rotateBlock() {
		int side = facing.ordinal() + 1;
		if(side == 6) side = 2;
		setFacing(side);
		return true;
	}
	
	public boolean setFacing(ForgeDirection facing){
		this.facing = facing;
		remapSides(true);
		return true;
	}
	
	public void setFacingFrom(Entity entity){
		setFacing(Helper.yaw2dir(entity.rotationYaw, 0, allowYAxisFacing()));
	}
	
	@Override
	public boolean setFacing(int side) {
		return setFacing(ForgeDirection.getOrientation(side));
	}
	
	private void remapSides(boolean update){
		setMap(facing, Face.FRONT);
		setMap(facing.getOpposite(), Face.BACK);
		
		EnumFacing left = facing.rotateAround(Axis.Y); // rotate on Y axis
		setMap(left, Face.LEFT);
		setMap(left.getOpposite(), Face.RIGHT);
		
		if(update) super.updateClients();
	}
	
	private void setMap(EnumFacing dir, Face face){
		faceMap[dir.ordinal()] = face;
	}
	
	// ##########################################################
	// Reconfigurable Sides
	
	@Override
	public boolean decrSide(int side) {
		FaceWrapper face = getFaceForSide(side);
		if(face.count() < 2) return false;
		if(this.side.isClient()){
			PacketMachineConfiguration pmc = new PacketMachineConfiguration();
			pmc.x = xCoord;
			pmc.y = yCoord;
			pmc.z = zCoord;
			pmc.increase = false;
			pmc.side = side;
			pmc.sendToServer();
		}else{
			face.decrease();
			super.updateClients();
		}
		return true;
	}
	
	@Override
	public boolean incrSide(int side) {
		FaceWrapper face = getFaceForSide(side);
		if(face.count() < 2) return false;
		if(this.side.isClient()){
			PacketMachineConfiguration pmc = new PacketMachineConfiguration();
			pmc.x = xCoord;
			pmc.y = yCoord;
			pmc.z = zCoord;
			pmc.increase = false;
			pmc.side = side;
			pmc.sendToServer();
		}else{
			face.increase();
			super.updateClients();
		}
		return true;
	}

	@Override
	public boolean setSide(int side, int config) {
		return false;
	}

	@Override
	public boolean resetSides() {
		if(this.side.isClient()){
			PacketMachineConfiguration pmc = new PacketMachineConfiguration();
			pmc.x = xCoord;
			pmc.y = yCoord;
			pmc.z = zCoord;
			pmc.reset = true;
			pmc.sendToServer();
		}else{
			for(FaceWrapper fw : faces.values()){
				fw.reset();
			}
			super.updateClients();
		}
		return true;
	}

	@Override
	public int getNumConfig(int side) {
		return getFaceForSide(side).count();
	}
	
	private FaceWrapper getFaceForSide(int side){
		return faces.get(faceMap[side]);
	}
	
	@Override
	public FaceColor getColorForSide(int side){
		if(side < 0 || side > 5) return FaceColor.VOID;
		return getFaceForSide(side).getColor();
	}
	
	@Override
	public FaceColor getColorForDirection(ForgeDirection dir){
		return getColorForSide(dir.ordinal());
	}
	
	@Override
	public IIcon getTexture(int side, int pass) {
		return getTexture(side, pass, side == facing.ordinal() && !getFaceForSide(side).getColor().isColored());
	}
	
	public IIcon getTexture(int side, int pass, boolean useFace) {
		FaceColor color = getFaceForSide(side).getColor();
		if(pass == 0){
			if(useFace) return getBlockType().getIcon(3, 0);
			String texture = null;
			if(side == 0) texture = TextureReference.MACHINE_BOTTOM;
			else if(side == 1) texture = TextureReference.MACHINE_TOP;
			else texture = TextureReference.MACHINE_SIDE;
			if(color.isColored()) texture += "I";
			return IconRegistry.blockIcons.get(texture);
		}else if(pass == 1 && !useFace){
			return IconRegistry.blockIcons.get(color.getTexture());
		}else return IconRegistry.blockIcons.get(TextureReference.TEXTURE_INVISIBLE);
	}
	
	// ##########################################################
	// Face Wrapper
	
	private static class FaceWrapper{
		private final FaceColor[] faces;
		private int face;
		
		// NBT ****************************************
		public FaceWrapper(int face, int ... faces){
			this.faces = new FaceColor[faces.length];
			this.face = face;
			for(int i = 0; i < faces.length; i++){
				this.faces[i] = FaceColor.values()[faces[i]];
			}
		}
		
		public int getFace(){return face;}
		
		public int[] getColors(){
			int[] faces = new int[this.faces.length];
			for(int i = 0; i < faces.length; i++){
				faces[i] = this.faces[i].ordinal();
			}
			return faces;
		}
		// End NBT ************************************
		
		public FaceWrapper(FaceColor ... faces){
			this.faces = faces;
			this.face = 0;
		}
		
		public FaceColor getColor(){
			return faces[face];
		}
		
		public int count(){
			return faces.length;
		}
		
		public void increase(){
			face++;
			if(face == count()){
				face -= count();
			}
		}
		
		public void decrease(){
			face--;
			if(face < 0){
				face += count();
			}
		}
		
		public void reset(){
			face = 0;
		}
	}
	
	// ##########################################################
	// Redstone Control
	
	@Override
	public void setPowered(boolean isPowered) {
		if(side.isServer()){
			redstonePower = isPowered;
			updateRS();
		}
	}

	@Override
	public boolean isPowered() {
		return redstonePower;
	}

	@Override
	public void setControl(ControlMode control) {
		if(side.isClient()){
			PacketMachineRedstone pmr = new PacketMachineRedstone();
			pmr.x = xCoord;
			pmr.y = yCoord;
			pmr.z = zCoord;
			pmr.mode = control;
			pmr.sendToServer();
		}else{
			redstoneControl = control;
			updateRS();
			updateClients();
		}
	}

	@Override
	public ControlMode getControl() {
		return redstoneControl;
	}
	
	private void updateRS(){
		rsContext.setUsesRedstone(!redstoneControl.isDisabled());
		rsContext.onRedstoneState(shouldRun());
	}
	
	private boolean shouldRun(){
		switch(redstoneControl){
			case DISABLED: return true;
			case LOW: return !redstonePower;
			case HIGH: return redstonePower;
			default: throw new RuntimeException("Is RedstoneControl null?");
		}
	}
	
	// ##########################################################
	// Nice little NBT handling
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		redstoneControl = ControlMode.values()[nbt.getInteger("rsControl")];
		redstonePower = nbt.getBoolean("rsPower");
		NBTTagCompound facingNBT = nbt.getCompoundTag("facing");
		facing = ForgeDirection.getOrientation(facingNBT.getInteger("facing"));
		faces = new EnumMap(Face.class);
		for(int i = 0; i < faceMap.length; i++){
			int f = facingNBT.getInteger("face_" + i);
			Face face = Face.values()[i];
			faceMap[i] = face;
			NBTTagCompound wrapper = facingNBT.getCompoundTag("fw_" + f);
			int fc = wrapper.getInteger("face");
			int[] c = wrapper.getIntArray("colors");
			faces.put(face, new FaceWrapper(fc, c));
		}
		NBTTagCompound components = nbt.getCompoundTag("components");
		int size = components.getInteger("size");
		for(int i = 0; i < size; i++){
			allComponents.get(i).readFromNBT(components.getCompoundTag("comp_" + i));
		}
		if(side.isClient() && worldObj != null){
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("rsControl", redstoneControl.ordinal());
		nbt.setBoolean("rsPower", redstonePower);
		NBTTagCompound facingNBT = new NBTTagCompound();
		facingNBT.setInteger("facing", facing.ordinal());
		for(int i = 0; i < faceMap.length; i++){
			Face face = faceMap[i];
			facingNBT.setInteger("face_" + i, face.ordinal());
			FaceWrapper fw = faces.get(face);
			NBTTagCompound wrapper = new NBTTagCompound();
			wrapper.setInteger("face", fw.getFace());
			wrapper.setIntArray("colors", fw.getColors());
			facingNBT.setTag("fw_" + face.ordinal(), wrapper);
		}
		nbt.setTag("facing", facingNBT);
		NBTTagCompound components = new NBTTagCompound();
		for(int i = 0; i < allComponents.size(); i++){
			components.setTag("comp_" + i, allComponents.get(i).writeToNBT(new NBTTagCompound()));
		}
		components.setInteger("size", allComponents.size());
		nbt.setTag("components", components);
	}
	
	// ##########################################################
	// SYNC OVERRIDE FOR COMPONENTS
	
	// I know, I know.
	// This only runs ONCE per TE at construction phase.
	private void cacheSyncValueArray(){
		LinkedList<Integer> keys = new LinkedList();
		int i;
		for(i = 0; i < syncComponents.size(); i++){
			ISyncedGUI.Flow sync = syncComponents.get(i);
			int[] vals = sync.getKeyArray();
			for(int val : vals){
				keys.add((COMPONENT_KEYS * i) + val);
			}
		}
		if(context instanceof ISyncedGUI.Source){
			int[] vals = ((ISyncedGUI.Source)context).getKeyArray();
			for(int val : vals){
				keys.add((COMPONENT_KEYS * i) + val);
			}
		}
		syncKeys = new int[keys.size()];
		int pos = 0;
		for(Integer key : keys){
			syncKeys[pos++] = key;
		}
	}
	
	@Override
	public int[] getKeyArray() {
		return syncKeys;
	}
	
	@Override
	public int getValue(int key) {
		int component = key / COMPONENT_KEYS;
		int actualKey = key % COMPONENT_KEYS;
		if(component < syncComponents.size()){
			return syncComponents.get(component).getValue(actualKey);
		}else{
			return super.getValue(actualKey);
		}
	}
	
	@Override
	public void setValue(int key, int val) {
		int component = key / COMPONENT_KEYS;
		int actualKey = key % COMPONENT_KEYS;
		if(component < syncComponents.size()){
			syncComponents.get(component).setValue(actualKey, val);
		}else{
			super.setValue(actualKey, val);
		}
	}
	
	// ##########################################################
	// COMPONENT: IBusComponent
	
	private ArrayList<IBusComponent> getInterfaces(){
		return (ArrayList<IBusComponent>) sidedComponents.get(IBusComponent.class);
	}
	
	/*@Override
	public IBusInterface[] getInterfaces(int side) {
		LinkedList<IBusInterface> interfaces = new LinkedList();
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		for(IBusComponent component : getInterfaces()){
			if(component.accessibleOnSide(dir)) interfaces.add(component.getInterface());
		}
		return interfaces.toArray(new IBusInterface[]{});
	}

	@Override
	public int getXCoord() {
		return xCoord;
	}

	@Override
	public int getYCoord() {
		return yCoord;
	}

	@Override
	public int getZCoord() {
		return zCoord;
	}

	@Override
	public void setEnabled(boolean enabled) {
		getInterfaces().get(0).setEnabled(enabled);
	}

	@Override
	public boolean getEnabled() {
		return getInterfaces().get(0).getEnabled();
	}

	@Override
	public void setAddress(short addr) {
		getInterfaces().get(0).setAddress(addr);
	}

	@Override
	public short getAddress() {
		return getInterfaces().get(0).getAddress();
	}*/
	
	// ##########################################################
	// COMPONENT: ITankComponent
	
	private ArrayList<ITankComponent> getTanks(){
		return (ArrayList<ITankComponent>) sidedComponents.get(ITankComponent.class);
	}
	
	/*@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
		for(ITankComponent tank : getTanks()){
			if(tank.canInputOnSide(from) && tank.canFill(resource.getFluid())){
				return tank.fill(resource, doFill);
			}
		}
		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
		return drain(from, resource.amount, doDrain);
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
		for(ITankComponent tank : getTanks()){
			if(tank.canOutputOnSide(from) && tank.canDrain()){
				return tank.drain(maxDrain, doDrain);
			}
		}
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid) {
		for(ITankComponent tank : getTanks()){
			if(tank.canInputOnSide(from)) return tank.canFill(fluid);
		}
		return false;
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid) {
		for(ITankComponent tank : getTanks()){
			if(tank.canOutputOnSide(from)) return tank.canDrain();
		}
		return false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from) {
		LinkedList<FluidTankInfo> infos = new LinkedList();
		for(ITankComponent tank : getTanks()){
			if(tank.canInputOnSide(from) || tank.canOutputOnSide(from)){
				infos.add(tank.getInfo());
			}
		}
		return infos.toArray(new FluidTankInfo[]{});
	}*/

	// ##########################################################
	// COMPONENT: ICapacitorComponent
	
	private ArrayList<ICapacitorComponent> getCapacitors(){
		return (ArrayList<ICapacitorComponent>) sidedComponents.get(ICapacitorComponent.class);
	}
	
	/*@Override
	public boolean canConnectEnergy(ForgeDirection from) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getEnergyStored(ForgeDirection from) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMaxEnergyStored(ForgeDirection from) {
		// TODO Auto-generated method stub
		return 0;
	}*/

	// ##########################################################
	// COMPONENT: IInventoryComponent
	
	private ArrayList<IInventoryComponent> getInventories(){
		return (ArrayList<IInventoryComponent>) sidedComponents.get(IInventoryComponent.class);
	}
	
	/** This is just a wrapper class to return 2 values
	 *  at once while keeping efficiency */
	/*private static final class InventoryData{
		IInventoryComponent inventory;
		int offset;
	}
	
	private InventoryData find(int slot){
		int slots = 0;
		if(slot >= 0)
			for(IInventoryComponent component : getInventories()){
				if(slot < slots + component.getSlotCount()){
					InventoryData data = new InventoryData();
					data.inventory = component;
					data.offset = slots;
					return data;
				}
				slots += component.getSlotCount();
			}
		return null;
	}
	
	private boolean isVisible(IInventoryComponent inv, int side){
		ForgeDirection dir = ForgeDirection.getOrientation(side);
		return inv.accessibleOnSide(dir)
			|| inv.canOutputOnSide(dir)
			|| inv.canInputOnSide(dir);
	}
	
	@Override
	public int getSizeInventory() {
		int slots = 0;
		for(IInventoryComponent component : getInventories()){
			slots += component.getSlotCount();
		}
		return slots;
	}
	
	@Override
	public ItemStack getStackInSlot(int slot) {
		InventoryData data = find(slot);
		if(data != null){
			return data.inventory.getStackInSlot(slot - data.offset);
		}
		return null;
	}
	
	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		InventoryData data = find(slot);
		if(data != null){
			return data.inventory.decrStackSize(slot - data.offset, amount);
		}
		return null;
	}
	
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		InventoryData data = find(slot);
		if(data != null){
			data.inventory.getStackInSlot(slot - data.offset);
		}
	}
	
	@Override public ItemStack getStackInSlotOnClosing(int slot){ return null; }
	@Override public String getInventoryName(){ return null; }
	@Override public boolean hasCustomInventoryName(){ return false; }
	@Override public int getInventoryStackLimit(){ return 64; }
	@Override public boolean isUseableByPlayer(EntityPlayer player) { return true; }
	@Override public void openInventory(){}
	@Override public void closeInventory(){}
	
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		InventoryData data = find(slot);
		if(data != null){
			return data.inventory.isItemValidForSlot(slot - data.offset, stack);
		}
		return false;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		TIntArrayList slots = new TIntArrayList();
		int current = 0;
		for(IInventoryComponent component : getInventories()){
			if(isVisible(component, side)){
				for(int s = 0; s < component.getSlotCount(); s++){
					slots.add(current);
					current++;
				}
			}else{
				current += component.getSlotCount();
			}
		}
		return slots.toArray();
	}
	
	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		InventoryData data = find(slot);
		if(data != null){
			return data.inventory.canInsertItem(slot - data.offset, stack, side);
		}
		return false;
	}
	
	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		InventoryData data = find(slot);
		if(data != null){
			return data.inventory.canExtractItem(slot - data.offset, stack, side);
		}
		return false;
	}*/
}