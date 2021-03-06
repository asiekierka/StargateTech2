package lordfokas.stargatetech2.modules.enemy;

import lordfokas.stargatetech2.ZZ_THRASH.BaseTileEntity__OLD_AND_FLAWED;
import lordfokas.stargatetech2.ZZ_THRASH.Vec3Int_THRASH;
import lordfokas.stargatetech2.api.shields.ShieldPermissions;
import lordfokas.stargatetech2.modules.enemy.tileentity.ShieldControllerCommon;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class TileShield extends BaseTileEntity__OLD_AND_FLAWED {
	private BlockPos controller;
	
	@Override
	public boolean canUpdate(){
		return false;
	}
	
	@Override
	protected void readNBT(NBTTagCompound nbt) {
		if(nbt.hasKey("master"))
			controller = Vec3Int_THRASH.fromNBT(nbt.getCompoundTag("master"));
	}

	@Override
	protected void writeNBT(NBTTagCompound nbt) {
		if(controller != null)
			nbt.setTag("master", controller);
	}
	
	public void setController(BlockPos controller){
		this.controller = controller;
	}
	
	public TileShieldController getController(){
		if(controller != null){
			TileEntity te = worldObj.getTileEntity(controller.x, controller.y, controller.z);
			if(te instanceof TileShieldController)
				return (TileShieldController) te;
			}
		return null;
	}
	
	public ShieldPermissions getPermissions(){
		if(controller != null){
			TileShieldController tsc = getController();
			if(tsc != null){
				return ((ShieldControllerCommon)tsc.getContext()).getPermissions();
			}
		}
		return ShieldPermissions.getDefault();
	}
	
	public String getOwner(){
		TileShieldController controller = getController();
		if(controller == null) return "";
		else return controller.getOwner();
	}
}