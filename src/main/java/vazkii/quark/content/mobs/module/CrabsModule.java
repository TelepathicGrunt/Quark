package vazkii.quark.content.mobs.module;

import com.google.common.collect.ImmutableSet;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements.Type;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import vazkii.arl.util.RegistryHelper;
import vazkii.quark.base.Quark;
import vazkii.quark.base.handler.BrewingHandler;
import vazkii.quark.base.handler.EntityAttributeHandler;
import vazkii.quark.base.handler.advancement.QuarkAdvancementHandler;
import vazkii.quark.base.handler.advancement.mod.BalancedDietModifier;
import vazkii.quark.base.handler.advancement.mod.FuriousCocktailModifier;
import vazkii.quark.base.handler.advancement.mod.TwoByTwoModifier;
import vazkii.quark.base.item.QuarkItem;
import vazkii.quark.base.module.LoadModule;
import vazkii.quark.base.module.ModuleCategory;
import vazkii.quark.base.module.QuarkModule;
import vazkii.quark.base.module.config.Config;
import vazkii.quark.base.module.config.type.CompoundBiomeConfig;
import vazkii.quark.base.module.config.type.EntitySpawnConfig;
import vazkii.quark.base.module.hint.Hint;
import vazkii.quark.base.recipe.ingredient.FlagIngredient;
import vazkii.quark.base.util.QuarkEffect;
import vazkii.quark.base.world.EntitySpawnHandler;
import vazkii.quark.content.mobs.client.render.entity.CrabRenderer;
import vazkii.quark.content.mobs.entity.Crab;

/**
 * @author WireSegal
 * Created at 7:28 PM on 9/22/19.
 */
@LoadModule(category = ModuleCategory.MOBS, hasSubscriptions = true)
public class CrabsModule extends QuarkModule {

	public static EntityType<Crab> crabType;

	@Config
	public static EntitySpawnConfig spawnConfig = new EntitySpawnConfig(5, 1, 3, CompoundBiomeConfig.fromBiomeTags(false, BiomeTags.IS_BEACH));

	public static TagKey<Block> crabSpawnableTag;
	public static MobEffect resilience;

	@Config(flag = "crab_brewing")
	public static boolean enableBrewing = true;
	
	@Config
	public static boolean enableResillienceEffect = true;
	
	@Hint(key = "crab_info") Item crab_leg;
	@Hint(key = "crab_info") Item crab_shell;

	@Override
	public void register() {
		crab_leg = new QuarkItem("crab_leg", this, new Item.Properties()
				.tab(CreativeModeTab.TAB_FOOD)
				.food(new FoodProperties.Builder()
						.meat()
						.nutrition(1)
						.saturationMod(0.3F)
						.build()));

		Item cookedCrabLeg = new QuarkItem("cooked_crab_leg", this, new Item.Properties()
				.tab(CreativeModeTab.TAB_FOOD)
				.food(new FoodProperties.Builder()
						.meat()
						.nutrition(8)
						.saturationMod(0.8F)
						.build()));

		crab_shell = new QuarkItem("crab_shell", this, new Item.Properties().tab(CreativeModeTab.TAB_BREWING))
				.setCondition(() -> enableBrewing);

		resilience = new QuarkEffect("resilience", MobEffectCategory.BENEFICIAL, 0x5b1a04);
		resilience.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, "2ddf3f0a-f386-47b6-aeb0-6bd32851f215", 0.5, AttributeModifier.Operation.ADDITION);

		BrewingHandler.addPotionMix("crab_brewing",
				() -> new FlagIngredient(Ingredient.of(crab_shell), "crab_brewing"), resilience);

		crabType = EntityType.Builder.<Crab>of(Crab::new, MobCategory.CREATURE)
				.sized(0.9F, 0.5F)
				.clientTrackingRange(8)
				.setCustomClientFactory((spawnEntity, world) -> new Crab(crabType, world))
				.build("crab");
		RegistryHelper.register(crabType, "crab", Registry.ENTITY_TYPE_REGISTRY);

		EntitySpawnHandler.registerSpawn(this, crabType, MobCategory.CREATURE, Type.ON_GROUND, Types.MOTION_BLOCKING_NO_LEAVES, Crab::spawnPredicate, spawnConfig);
		EntitySpawnHandler.addEgg(crabType, 0x893c22, 0x916548, spawnConfig);

		EntityAttributeHandler.put(crabType, Crab::prepareAttributes);
		
		QuarkAdvancementHandler.addModifier(new FuriousCocktailModifier(this, () -> enableBrewing, ImmutableSet.of(resilience))
				.setCondition(() -> enableResillienceEffect));
		QuarkAdvancementHandler.addModifier(new TwoByTwoModifier(this, ImmutableSet.of(crabType)));
		QuarkAdvancementHandler.addModifier(new BalancedDietModifier(this, ImmutableSet.of(crab_leg, cookedCrabLeg)));
	}

	@Override
	public void setup() {
		crabSpawnableTag = BlockTags.create(new ResourceLocation(Quark.MOD_ID, "crab_spawnable"));
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void clientSetup() {
		EntityRenderers.register(crabType, CrabRenderer::new);
	}
}
