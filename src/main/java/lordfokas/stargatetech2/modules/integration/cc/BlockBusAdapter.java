package lordfokas.stargatetech2.modules.integration.cc;

public class BlockBusAdapter /*extends BaseBlockContainer__OLD_AND_FLAWED*/ {
	
	/*public BlockBusAdapter() {
		super(BlockReference.BUS_ADAPTER);
		setIsAbstractBusBlock();
	}

	@Override
	protected BaseTileEntity__OLD_AND_FLAWED createTileEntity(int metadata) {
		return new TileBusAdapter();
	}
	
	@Override
	public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int s, float hx, float hy, float hz){
		ItemStack stack = p.inventory.getCurrentItem();
		Item item = stack != null ? stack.getItem() : null;
		if(item instanceof IToolWrench){
			IToolWrench wrench = (IToolWrench) item;
			if(wrench.canWrench(p, x, y, z)){
				dropBlockAsItem(w, x, y, z, 0, 0);
				w.setBlock(x, y, z, Blocks.air, 0, 3);
				wrench.wrenchUsed(p, x, y, z);
				return true;
			}
		}
		return false;
	}*/
}