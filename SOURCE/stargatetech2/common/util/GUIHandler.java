package stargatetech2.common.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import stargatetech2.common.base.BaseContainer;
import stargatetech2.common.base.BaseGUI;
import stargatetech2.core.gui.ContainerShieldEmitter;
import stargatetech2.core.gui.GUIParticleIonizer;
import stargatetech2.core.gui.GUIShieldEmitter;
import stargatetech2.core.tileentity.TileParticleIonizer;
import stargatetech2.core.tileentity.TileShieldEmitter;
import cpw.mods.fml.common.network.IGuiHandler;

public class GUIHandler implements IGuiHandler {
	public enum Screen{
		SHIELD_EMITTER,
		PARTICLE_IONIZER
	}
	
	public BaseContainer getContainer(int ID, EntityPlayer player, World world, int x, int y, int z){
		TileEntity te = world.getBlockTileEntity(x, y, z);
		BaseContainer container = null;
		switch(Screen.values()[ID]){
			case SHIELD_EMITTER:
				if(te instanceof TileShieldEmitter)
					container = new ContainerShieldEmitter((TileShieldEmitter)te);
				break;
			case PARTICLE_IONIZER:
				if(te instanceof TileParticleIonizer)
					container = new BaseContainer((TileParticleIonizer)te);
				break;
			default:
				break;
		}
		return container;
	}
	
	@Override
	public BaseContainer getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		BaseContainer container = getContainer(ID, player, world, x, y, z);
		container.forceClientUpdate();
		return container;
	}
	
	@Override
	public BaseGUI getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		BaseContainer container = getContainer(ID, player, world, x, y, z);
		BaseGUI gui = null;
		switch(Screen.values()[ID]){
			case SHIELD_EMITTER:
				gui = new GUIShieldEmitter(container);
				break;
			case PARTICLE_IONIZER:
				gui = new GUIParticleIonizer(container);
				break;
			default:
				break;
		}
		return gui;
	}
}