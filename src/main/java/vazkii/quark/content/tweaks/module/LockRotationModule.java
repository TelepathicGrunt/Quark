package vazkii.quark.content.tweaks.module;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import vazkii.arl.network.MessageSerializer;
import vazkii.quark.api.IRotationLockable;
import vazkii.quark.base.client.handler.ModKeybindHandler;
import vazkii.quark.base.handler.MiscUtil;
import vazkii.quark.base.module.LoadModule;
import vazkii.quark.base.module.ModuleCategory;
import vazkii.quark.base.module.ModuleLoader;
import vazkii.quark.base.module.QuarkModule;
import vazkii.quark.base.network.QuarkNetwork;
import vazkii.quark.base.network.message.SetLockProfileMessage;
import vazkii.quark.content.building.block.VerticalSlabBlock;
import vazkii.quark.content.building.block.VerticalSlabBlock.VerticalSlabType;

@LoadModule(category = ModuleCategory.TWEAKS, hasSubscriptions = true)
public class LockRotationModule extends QuarkModule {

	private static final String TAG_LOCKED_ONCE = "quark:locked_once";

	private static final HashMap<UUID, LockProfile> lockProfiles = new HashMap<>();

	@OnlyIn(Dist.CLIENT)
	private LockProfile clientProfile;

	@OnlyIn(Dist.CLIENT)
	private KeyMapping keybind;

	@Override
	public void configChanged() {
		lockProfiles.clear();
	}

	@Override
	public void setup() {
		MessageSerializer.mapHandlers(LockProfile.class, LockProfile::readProfile, LockProfile::writeProfile);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void registerKeybinds(RegisterKeyMappingsEvent event) {
		keybind = ModKeybindHandler.init(event, "lock_rotation", "k", ModKeybindHandler.MISC_GROUP);
	}

	public static BlockState fixBlockRotation(BlockState state, BlockPlaceContext ctx) {
		if (state == null || ctx.getPlayer() == null || !ModuleLoader.INSTANCE.isModuleEnabled(LockRotationModule.class))
			return state;

		UUID uuid = ctx.getPlayer().getUUID();
		if(lockProfiles.containsKey(uuid)) {
			LockProfile profile = lockProfiles.get(uuid);
			BlockState transformed = getRotatedState(ctx.getLevel(), ctx.getClickedPos(), state, profile.facing.getOpposite(), profile.half);

			if(!transformed.equals(state))
				return Block.updateFromNeighbourShapes(transformed, ctx.getLevel(), ctx.getClickedPos());
		}

		return state;
	}

	public static BlockState getRotatedState(Level world, BlockPos pos, BlockState state, Direction face, int half) {
		BlockState setState = state;
		ImmutableMap<Property<?>, Comparable<?>> props = state.getValues();
		Block block = state.getBlock();


		if(block instanceof IRotationLockable lockable)
			setState = lockable.applyRotationLock(world, pos, state, face, half);

		// General Facing
		else if(props.containsKey(BlockStateProperties.FACING))
			setState = state.setValue(BlockStateProperties.FACING, face);

		// Vertical Slabs
		else if (props.containsKey(VerticalSlabBlock.TYPE) && props.get(VerticalSlabBlock.TYPE) != VerticalSlabType.DOUBLE && face.getAxis() != Axis.Y)
			setState = state.setValue(VerticalSlabBlock.TYPE, Objects.requireNonNull(VerticalSlabType.fromDirection(face)));

		// Horizontal Facing
		else if(props.containsKey(BlockStateProperties.HORIZONTAL_FACING) && face.getAxis() != Axis.Y) {
			if(block instanceof StairBlock)
				setState = state.setValue(BlockStateProperties.HORIZONTAL_FACING, face.getOpposite());
			else setState = state.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
		}

		// Pillar Axis
		else if(props.containsKey(BlockStateProperties.AXIS))
			setState = state.setValue(BlockStateProperties.AXIS, face.getAxis());

		// Hopper Facing
		else if(props.containsKey(BlockStateProperties.FACING_HOPPER))
			setState = state.setValue(BlockStateProperties.FACING_HOPPER, face == Direction.DOWN ? face : face.getOpposite());

		// Half
		if(half != -1) {
			// Slab type
			if(props.containsKey(BlockStateProperties.SLAB_TYPE) && props.get(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE)
				setState = setState.setValue(BlockStateProperties.SLAB_TYPE, half == 1 ? SlabType.TOP : SlabType.BOTTOM);

			// Half (stairs)
			else if(props.containsKey(BlockStateProperties.HALF))
				setState = setState.setValue(BlockStateProperties.HALF, half == 1 ? Half.TOP : Half.BOTTOM);
		}

		return setState;
	}

	@SubscribeEvent
	public void onPlayerLogoff(PlayerLoggedOutEvent event) {
		lockProfiles.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onMouseInput(InputEvent.MouseButton event) {
		acceptInput();
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onKeyInput(InputEvent.Key event) {
		acceptInput();
	}

	private void acceptInput() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = keybind.isDown();
		if(mc.isWindowActive() && down && mc.screen == null) {
			LockProfile newProfile;
			HitResult result = mc.hitResult;

			if(result instanceof BlockHitResult bresult && result.getType() == Type.BLOCK) {
				Vec3 hitVec = bresult.getLocation();
				Direction face = bresult.getDirection();

				int half = Math.abs((int) ((hitVec.y - (int) hitVec.y) * 2));
				if(face.getAxis() == Axis.Y)
					half = -1;
				else if(hitVec.y < 0)
					half = 1 - half;

				newProfile = new LockProfile(face.getOpposite(), half);

			} else {
				Vec3 look = mc.player.getLookAngle();
				newProfile = new LockProfile(Direction.getNearest((float) look.x, (float) look.y, (float) look.z), -1);
			}

			if(clientProfile != null && clientProfile.equals(newProfile))
				clientProfile = null;
			else clientProfile = newProfile;
			QuarkNetwork.sendToServer(new SetLockProfileMessage(clientProfile));
		}
	}

	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void onHUDRender(RenderGuiOverlayEvent.Post event) {
		if(event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type() && clientProfile != null) {
			PoseStack matrix = event.getPoseStack();

			RenderSystem.enableBlend();
			RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
			RenderSystem.setShaderTexture(0, MiscUtil.GENERAL_ICONS);

			Window window = event.getWindow();
			int x = window.getGuiScaledWidth() / 2 + 20;
			int y = window.getGuiScaledHeight() / 2 - 8;
			Screen.blit(matrix, x, y, clientProfile.facing.ordinal() * 16, 65, 16, 16, 256, 256);

			if(clientProfile.half > -1)
				Screen.blit(matrix, x + 16, y, clientProfile.half * 16, 79, 16, 16, 256, 256);

			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

		}
	}

	public static void setProfile(Player player, LockProfile profile) {
		UUID uuid = player.getUUID();

		if(profile == null)
			lockProfiles.remove(uuid);
		else {
			boolean locked = player.getPersistentData().getBoolean(TAG_LOCKED_ONCE);
			if(!locked) {
				Component keybind = Component.keybind("quark.keybind.lock_rotation").withStyle(ChatFormatting.AQUA);
				Component text = Component.translatable("quark.misc.rotation_lock", keybind);
				player.sendSystemMessage(text);

				player.getPersistentData().putBoolean(TAG_LOCKED_ONCE, true);
			}

			lockProfiles.put(uuid, profile);
		}
	}

	public record LockProfile(Direction facing, int half) {

		public static LockProfile readProfile(FriendlyByteBuf buf, Field field) {
			boolean valid = buf.readBoolean();
			if (!valid)
				return null;

			int face = buf.readInt();
			int half = buf.readInt();
			return new LockProfile(Direction.from3DDataValue(face), half);
		}

		public static void writeProfile(FriendlyByteBuf buf, Field field, LockProfile p) {
			if (p == null)
				buf.writeBoolean(false);
			else {
				buf.writeBoolean(true);
				buf.writeInt(p.facing.get3DDataValue());
				buf.writeInt(p.half);
			}
		}

		@Override
		public boolean equals(Object other) {
			if (other == this)
				return true;
			if (!(other instanceof LockProfile otherProfile))
				return false;

			return otherProfile.facing == facing && otherProfile.half == half;
		}

		@Override
		public int hashCode() {
			return facing.hashCode() * 31 + half;
		}
	}

}
