package vazkii.quark.content.world.module;

import java.util.List;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MaterialColor;
import vazkii.quark.api.IIndirectConnector;
import vazkii.quark.base.Quark;
import vazkii.quark.base.block.QuarkInheritedPaneBlock;
import vazkii.quark.base.handler.ToolInteractionHandler;
import vazkii.quark.base.module.LoadModule;
import vazkii.quark.base.module.ModuleCategory;
import vazkii.quark.base.module.ModuleLoader;
import vazkii.quark.base.module.config.Config;
import vazkii.quark.base.module.hint.Hint;
import vazkii.quark.content.tools.module.BeaconRedirectionModule;
import vazkii.quark.content.world.block.CorundumBlock;
import vazkii.quark.content.world.block.CorundumClusterBlock;
import vazkii.quark.content.world.undergroundstyle.CorundumStyle;
import vazkii.quark.content.world.undergroundstyle.base.AbstractUndergroundStyleModule;
import vazkii.quark.content.world.undergroundstyle.base.UndergroundStyleConfig;

@LoadModule(category = ModuleCategory.WORLD)
public class CorundumModule extends AbstractUndergroundStyleModule<CorundumStyle> {

	@Config
	@Config.Min(value = 0)
	@Config.Max(value = 1)
	public static double crystalChance = 0.16;

	@Config
	@Config.Min(value = 0)
	@Config.Max(value = 1)
	public static double crystalClusterChance = 0.2;

	@Config
	@Config.Min(value = 0)
	@Config.Max(value = 1)
	public static double crystalClusterOnSidesChance = 0.6;

	@Config
	@Config.Min(value = 0)
	@Config.Max(value = 1)
	public static double doubleCrystalChance = 0.2;

	@Config(description = "The chance that a crystal can grow, this is on average 1 in X world ticks, set to a higher value to make them grow slower. Minimum is 1, for every tick. Set to 0 to disable growth.")
	public static int caveCrystalGrowthChance = 5;

	@Config(flag = "cave_corundum_runes")
	public static boolean crystalsCraftRunes = true;

	@Config public static boolean enableCollateralMovement = true;

	public static boolean staticEnabled;

	public static List<CorundumBlock> crystals = Lists.newArrayList();
	public static List<CorundumClusterBlock> clusters = Lists.newArrayList();
	@Hint public static TagKey<Block> corundumTag;

	@Override
	public void register() {
		add("red", 0xff0000, MaterialColor.COLOR_RED);
		add("orange", 0xff8000, MaterialColor.COLOR_ORANGE);
		add("yellow", 0xffff00, MaterialColor.COLOR_YELLOW);
		add("green", 0x00ff00, MaterialColor.COLOR_GREEN);
		add("blue", 0x00ffff, MaterialColor.COLOR_LIGHT_BLUE);
		add("indigo", 0x0000ff, MaterialColor.COLOR_BLUE);
		add("violet", 0xff00ff, MaterialColor.COLOR_MAGENTA);
		add("white", 0xffffff, MaterialColor.SNOW);
		add("black", 0x000000, MaterialColor.COLOR_BLACK);

		super.register();
	}

	@Override
	public void configChanged() {
		staticEnabled = enabled;
	}

	@Override
	public void addAdditionalHints(BiConsumer<Item, Component> consumer) {
		MutableComponent comp = Component.translatable("quark.jei.hint.corundum_cluster_grow");
		
		if(ModuleLoader.INSTANCE.isModuleEnabled(BeaconRedirectionModule.class))
			comp = comp.append(" ").append(Component.translatable("quark.jei.hint.corundum_cluster_redirect"));
		
		for(Block block : clusters)
			consumer.accept(block.asItem(), comp);
	}
	
	private void add(String name, int color, MaterialColor material) {
		CorundumBlock crystal = new CorundumBlock(name + "_corundum", color, this, material, false);
		crystals.add(crystal);

		CorundumBlock waxed = new CorundumBlock("waxed_" + name + "_corundum", color, this, material, true);
		ToolInteractionHandler.registerWaxedBlock(this, crystal, waxed);

		new QuarkInheritedPaneBlock(crystal);
		CorundumClusterBlock cluster = new CorundumClusterBlock(crystal);
		clusters.add(cluster);

		ClusterConnection connection = new ClusterConnection(cluster);
		IIndirectConnector.INDIRECT_STICKY_BLOCKS.add(Pair.of(connection::isValidState, connection));
	}

	@Override
	public void setup() {
		super.setup();
		corundumTag = BlockTags.create(new ResourceLocation(Quark.MOD_ID, "corundum"));
	}

	@Override
	protected String getStyleName() {
		return "corundum";
	}

	@Override
	protected UndergroundStyleConfig<CorundumStyle> getStyleConfig() {
		return new UndergroundStyleConfig<>(new CorundumStyle(), 400, true, BiomeTags.IS_OCEAN).setDefaultSize(72, 20, 22, 4);
	}

	public record ClusterConnection(CorundumClusterBlock cluster) implements IIndirectConnector {

		@Override
		public boolean isEnabled() {
			return enableCollateralMovement;
		}

		private boolean isValidState(BlockState state) {
			return state.getBlock() == cluster;
		}

		@Override
		public boolean canConnectIndirectly(Level world, BlockPos ourPos, BlockPos sourcePos, BlockState ourState, BlockState sourceState) {
			BlockPos offsetPos = ourPos.relative(ourState.getValue(CorundumClusterBlock.FACING).getOpposite());
			if (!offsetPos.equals(sourcePos))
				return false;

			return sourceState.getBlock() == cluster.base;
		}

	}

}
