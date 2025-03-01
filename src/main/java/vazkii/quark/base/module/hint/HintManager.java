package vazkii.quark.base.module.hint;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import vazkii.arl.util.RegistryHelper;
import vazkii.quark.base.Quark;
import vazkii.quark.base.module.QuarkModule;
import vazkii.quark.base.module.config.ConfigFlagManager;

public class HintManager {

	public static void loadHints(List<Field> fields, ConfigFlagManager flagManager, QuarkModule module) {
		for(Field f : fields) {
			f.setAccessible(true);
			Hint hint = f.getDeclaredAnnotation(Hint.class);

			if(hint != null) {
				List<Supplier<Object>> list = new ArrayList<>();
				outer:
				for(var h : hint.content()){
					for(Field cf : fields) {
						if(cf.getName().equals(h)){
							list.add(()->{
								try {
									return cf.get(module);
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							});
							continue outer;
						}
					}
					throw new AssertionError(String.format("Missing field %s in module %s for hin %s", h,module, f));
				}

				HintObject hintObj = new HintObject(flagManager, module, hint, list, () -> {
					try {
						return Optional.ofNullable(f.get(module));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});

				module.hints.add(hintObj);
			}
		}
	}

	public static void hintItem(BiConsumer<Item, Component> consumer, ItemLike itemLike, Object... extraContent) {
		Item item = itemLike.asItem();
		ResourceLocation res = RegistryHelper.getRegistryName(item, Registry.ITEM);
		String ns = res.getNamespace();
		String path = res.getPath();

		if(ns.equals(Quark.MOD_ID))
			ns = "";
		else ns += ".";

		hintItem(consumer, item, ns + path);
	}

	public static void hintItem(BiConsumer<Item, Component> consumer, ItemLike itemLike, String key, Object... extraContent) {
		Item item = itemLike.asItem();
		String hint = "quark.jei.hint." + key;
		consumer.accept(item, Component.translatable(hint, extraContent));
	}

}
