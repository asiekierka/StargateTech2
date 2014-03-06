package stargatetech2.enemy.tileentity;

import java.util.LinkedList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import stargatetech2.core.api.ParticleIonizerRecipes;
import stargatetech2.core.api.ParticleIonizerRecipes.IonizerRecipe;
import stargatetech2.core.machine.FaceColor;
import stargatetech2.core.machine.Inventory;
import stargatetech2.core.machine.TileEntityMachine;
import stargatetech2.enemy.ModuleEnemy;
import stargatetech2.enemy.block.BlockParticleIonizer;
import stargatetech2.enemy.util.IonizedParticles;
import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;

public class TileParticleIonizer extends TileEntityMachine implements IFluidHandler, IEnergyHandler, ISidedInventory{
	public final FluidTank ionizedParticles = new FluidTank(8000);		// orange
	public final FluidTank fluidIonizable = new FluidTank(8000);		// blue
	public final Inventory solidIonizable = new Inventory(9);			// blue
	public final EnergyStorage energy = new EnergyStorage(32000, 400);
	private IonizerRecipe recipe = null;
	private int ticksLeft = 0;
	private long nextSearch = 0;
	
	@Override
	public void invalidate(){
		super.invalidate();
		BlockParticleIonizer block = ModuleEnemy.particleIonizer;
		for(int slot = 0; slot < solidIonizable.getSizeInventory(); slot++){
			ItemStack stack = solidIonizable.getStackInSlot(slot);
			if(stack != null){
				block.dropItemStack(worldObj, xCoord, yCoord, zCoord, stack);
			}
		}
	}
	
	@Override
	public void updateEntity(){
		if(!worldObj.isRemote){
			if(recipe != null){
				work();
			}
			if(recipe == null && worldObj.getTotalWorldTime() >= nextSearch){
				findRecipe();
				nextSearch = worldObj.getTotalWorldTime() + 20;
			}
		}
	}
	
	private void work(){
		if(ticksLeft > 0){
			if(ionizedParticles.getCapacity() - ionizedParticles.getFluidAmount() >= recipe.ions && energy.getEnergyStored() >= recipe.power){
				ionizedParticles.fill(new FluidStack(IonizedParticles.fluid, recipe.ions), true);
				energy.extractEnergy(recipe.power, false);
				ticksLeft--;
			}
		}else{
			FluidStack fluid = fluidIonizable.getFluid();
			ItemStack[] inventory = getInventory();
			if(recipe.checkMatch(fluid, inventory)){
				FluidStack fs = recipe.getFluid();
				if(fs != null){
					fluidIonizable.drain(fs.amount, true);
				}
				ItemStack is = recipe.getSolid();
				if(is != null){
					for(int i = 0; i < solidIonizable.getSizeInventory(); i++){
						ItemStack stack = solidIonizable.getStackInSlot(i);
						if(stack == null) continue;
						stack = stack.copy();
						stack.stackSize = is.stackSize;
						if(ItemStack.areItemStacksEqual(is, stack)){
							solidIonizable.decrStackSize(i, 1);
							break;
						}
					}
				}
				ticksLeft = recipe.time;
			}else{
				recipe = null;
			}
		}
	}
	
	private void findRecipe(){
		FluidStack fluid = fluidIonizable.getFluid();
		ItemStack[] inventory = getInventory();
		LinkedList<IonizerRecipe> solids, fluids, both;
		solids = new LinkedList();
		fluids = new LinkedList();
		both = new LinkedList();
		for(IonizerRecipe r : ParticleIonizerRecipes.recipes().getRecipes()){
			if(r.checkMatch(fluid, inventory)){
				if(r.getFluid() != null && r.getSolid() != null){
					both.add(r);
				}else if(r.getFluid() != null){
					fluids.add(r);
				}else{
					solids.add(r);
				}
			}
		}
		if(!both.isEmpty()){
			pickRecipe(both);
		}else if(!solids.isEmpty()){
			pickRecipe(solids);
		}else if(!fluids.isEmpty()){
			pickRecipe(fluids);
		}
	}
	
	private void pickRecipe(LinkedList<IonizerRecipe> list){
		IonizerRecipe pick = null;
		for(IonizerRecipe r : list){
			if(pick == null || pick.ions < r.ions || (pick.ions == r.ions && pick.power > r.power)){
				pick = r;
			}
		}
		recipe = pick;
		ticksLeft = 0;
	}
	
	private ItemStack[] getInventory(){
		ItemStack[] inventory = new ItemStack[solidIonizable.getSizeInventory()];
		for(int i = 0; i < inventory.length; i++){
			inventory[i] = solidIonizable.getStackInSlot(i);
		}
		return inventory;
	}
	
	public int getWorkLeft(){
		return ticksLeft;
	}
	
	public IonizerRecipe getRecipeInstance(){
		return recipe;
	}
	
	public int getRecipe(){
		if(recipe == null) return -1;
		else return ParticleIonizerRecipes.recipes().getRecipeID(recipe);
	}
	
	public void setRecipe(int r){
		recipe = ParticleIonizerRecipes.recipes().getRecipe(r);
	}
	
	@Override
	protected void readNBT(NBTTagCompound nbt){
		ionizedParticles.readFromNBT(nbt.getCompoundTag("ionizedParticles"));
		fluidIonizable.readFromNBT(nbt.getCompoundTag("fluidIonizable"));
		solidIonizable.readFromNBT(nbt.getCompoundTag("solidIonizable"));
		energy.readFromNBT(nbt.getCompoundTag("energy"));
		readFacingNBT(nbt.getCompoundTag("facing"));
		setRecipe(nbt.getInteger("recipe"));
		ticksLeft = nbt.getInteger("ticksLeft");
		nextSearch = nbt.getLong("nextSearch");
	}
	
	@Override
	protected void writeNBT(NBTTagCompound nbt){
		nbt.setCompoundTag("ionizedParticles", ionizedParticles.writeToNBT(new NBTTagCompound()));
		nbt.setCompoundTag("fluidIonizable", fluidIonizable.writeToNBT(new NBTTagCompound()));
		nbt.setCompoundTag("solidIonizable", solidIonizable.writeToNBT(new NBTTagCompound()));
		nbt.setCompoundTag("energy", energy.writeToNBT(new NBTTagCompound()));
		nbt.setCompoundTag("facing", writeFacingNBT());
		nbt.setInteger("recipe", getRecipe());
		nbt.setInteger("ticksLeft", ticksLeft);
		nbt.setLong("nextSearch", nextSearch);
	}
	
	@Override
	protected FaceColor[] getPossibleFaceColors() {
		return new FaceColor[]{FaceColor.VOID, FaceColor.BLUE, FaceColor.ORANGE};
	}
	
	// ############################################################################################
	// IFluidHandler
	@Override
	public int fill(ForgeDirection side, FluidStack resource, boolean doFill) {
		if(getColor(side) == FaceColor.BLUE && ParticleIonizerRecipes.recipes().isIonizable(resource)){
			return fluidIonizable.fill(resource, doFill);
		}
		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection side, FluidStack resource, boolean doDrain) {
		FluidStack drain = drain(side, resource.amount, false);
		if(drain == null || !resource.isFluidEqual(drain)) return null;
		else return drain(side, resource.amount, doDrain);
	}

	@Override
	public FluidStack drain(ForgeDirection side, int maxDrain, boolean doDrain) {
		if(getColor(side) == FaceColor.ORANGE){
			return ionizedParticles.drain(maxDrain, doDrain);
		}
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection side, Fluid fluid) {
		return getColor(side) == FaceColor.BLUE;
	}

	@Override
	public boolean canDrain(ForgeDirection side, Fluid fluid) {
		return getColor(side) == FaceColor.ORANGE;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection side) {
		if(getColor(side) == FaceColor.BLUE){
			return new FluidTankInfo[]{fluidIonizable.getInfo()};
		}else if(getColor(side) == FaceColor.ORANGE){
			return new FluidTankInfo[]{ionizedParticles.getInfo()};
		}else return null;
	}
	
	// ############################################################################################
	// IEnergyHandler
	@Override
	public int receiveEnergy(ForgeDirection side, int maxReceive, boolean simulate) {
		return energy.receiveEnergy(maxReceive, simulate);
	}

	@Override
	public int extractEnergy(ForgeDirection side, int maxExtract, boolean simulate) {
		return 0;
	}

	@Override
	public boolean canInterface(ForgeDirection side) {
		return true;
	}

	@Override
	public int getEnergyStored(ForgeDirection side) {
		return energy.getEnergyStored();
	}

	@Override
	public int getMaxEnergyStored(ForgeDirection side) {
		return energy.getMaxEnergyStored();
	}
	
	// ############################################################################################
	// ISidedInventory
	@Override
	public int getSizeInventory() {
		return solidIonizable.getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return solidIonizable.getStackInSlot(slot);
	}

	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		return solidIonizable.decrStackSize(slot, amount);
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		solidIonizable.setInventorySlotContents(slot, stack);
	}
	
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return ParticleIonizerRecipes.recipes().isIonizable(stack);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		if(getColor(side) == FaceColor.BLUE){
			return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
		}
		return new int[]{};
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return getColor(side) == FaceColor.BLUE && solidIonizable.canInsert();
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return getColor(side) == FaceColor.BLUE && solidIonizable.canExtract();
	}
	
	// useless stuff...  :c  (creeper face!)
	@Override public void openChest(){}
	@Override public void closeChest(){}
	@Override public boolean isUseableByPlayer(EntityPlayer entityplayer){ return true; }
	@Override public int getInventoryStackLimit(){ return 64; }
	@Override public String getInvName(){ return "Particle ionizer"; }
	@Override public boolean isInvNameLocalized(){ return true; }
	@Override public ItemStack getStackInSlotOnClosing(int slot){ return null; }
}