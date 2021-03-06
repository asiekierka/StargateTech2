package lordfokas.stargatetech2.modules.world;

import lordfokas.naquadria.render.Color;
import lordfokas.stargatetech2.ZZ_THRASH.BaseISBRH_THRASH;
import lordfokas.stargatetech2.modules.ModuleWorld;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

public class RenderLanteanWall extends BaseISBRH_THRASH {
	private static final RenderLanteanWall INSTANCE = new RenderLanteanWall();
	
	public static RenderLanteanWall instance(){
		return INSTANCE;
	}
	
	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
		Color color = getRenderColor(world.getBlockMetadata(x, y, z));
		renderer.renderStandardBlockWithColorMultiplier(block, x, y, z, color.r(), color.g(), color.b());
		return true;
	}
	
	@Override
	protected Color getRenderColor(int metadata){
		return ModuleWorld.lanteanWall.getColor(metadata);
	}
}
