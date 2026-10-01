package com.example.godcore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GodCoreMod implements ModInitializer {
    private static final int CRATER_RADIUS = 100;
    private static final int CRATER_DEPTH = 54;
    private static final Queue<CraterJob> CRATER_JOBS = new ArrayDeque<>();
    private static final Map<UUID, CaboomCharge> CABOOM_CHARGES = new HashMap<>();

    @Override
    public void onInitialize() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);

            if (stack.is(Items.ECHO_SHARD) && hasMarker(stack, "godcore_caboom")) {
                HitResult hit = player.pick(160.0, 1.0F, false);
                Vec3 point = hit.getType() == HitResult.Type.BLOCK
                        ? hit.getLocation()
                        : player.getEyePosition().add(player.getLookAngle().scale(80.0));
                if (CABOOM_CHARGES.containsKey(player.getUUID())) {
                    player.displayClientMessage(Component.literal("Caboom is already charging!"), true);
                    return InteractionResult.SUCCESS;
                }
                BlockPos center = BlockPos.containing(point);
                CABOOM_CHARGES.put(player.getUUID(), new CaboomCharge(serverLevel, center, 60));
                player.displayClientMessage(Component.literal("CABOOM CHARGING — 3 seconds!"), true);
                serverLevel.playSound(null, center, SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.PLAYERS, 2.0F, 1.8F);
                return InteractionResult.SUCCESS;
            }

            if (stack.is(Items.FEATHER) && hasMarker(stack, "godcore_flight")) {
                toggleFlight(player);
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, CaboomCharge>> charges = CABOOM_CHARGES.entrySet().iterator();
            while (charges.hasNext()) {
                Map.Entry<UUID, CaboomCharge> entry = charges.next();
                CaboomCharge charge = entry.getValue();
                ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
                if (caster == null || !caster.isAlive()) {
                    charges.remove();
                    continue;
                }

                charge.ticksRemaining--;
                if (charge.ticksRemaining <= 0) {
                    eliminatePlayersInBlast(charge.level, charge.center, caster);
                    startCrater(charge.level, charge.center, caster);
                    caster.displayClientMessage(Component.literal("CABOOM!"), true);
                    charges.remove();
                } else if (charge.ticksRemaining % 5 == 0) {
                    showChargeEffect(charge, caster);
                }
            }

            CraterJob job = CRATER_JOBS.peek();
            if (job != null && job.process(300)) CRATER_JOBS.remove();
        });
    }

    private static boolean hasMarker(ItemStack stack, String marker) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        return tag.getBooleanOr(marker, false);
    }

    private static void toggleFlight(Player player) {
        boolean enabled = player.getTags().contains("godcore_flight");
        if (enabled) {
            player.removeTag("godcore_flight");
            player.getAbilities().mayfly = player.getAbilities().instabuild || player.isSpectator();
            if (!player.getAbilities().mayfly) player.getAbilities().flying = false;
            player.displayClientMessage(Component.literal("Flight OFF"), true);
        } else {
            player.addTag("godcore_flight");
            player.getAbilities().mayfly = true;
            player.displayClientMessage(Component.literal("Flight ON"), true);
        }
        player.onUpdateAbilities();
    }

    private static void showChargeEffect(CaboomCharge charge, ServerPlayer caster) {
        int seconds = Math.max(1, (charge.ticksRemaining + 19) / 20);
        caster.displayClientMessage(Component.literal("CABOOM CHARGING — " + seconds + "..."), true);
        double progress = 1.0 - charge.ticksRemaining / 60.0;
        double radius = 3.0 + progress * 12.0;
        double y = charge.center.getY() + 1.0;
        for (int i = 0; i < 32; i++) {
            double angle = (Math.PI * 2.0 * i) / 32.0;
            double x = charge.center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = charge.center.getZ() + 0.5 + Math.sin(angle) * radius;
            charge.level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 1, 0, 0, 0, 0);
        }
        charge.level.sendParticles(ParticleTypes.SONIC_BOOM,
                charge.center.getX() + 0.5, y, charge.center.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    private static void eliminatePlayersInBlast(ServerLevel level, BlockPos center, ServerPlayer caster) {
        double radiusSquared = (double) CRATER_RADIUS * CRATER_RADIUS;
        for (ServerPlayer target : level.players()) {
            if (target == caster) continue;
            if (target.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5)
                    <= radiusSquared) {
                target.kill();
            }
        }
    }

    private static final class CaboomCharge {
        private final ServerLevel level;
        private final BlockPos center;
        private int ticksRemaining;

        private CaboomCharge(ServerLevel level, BlockPos center, int ticksRemaining) {
            this.level = level;
            this.center = center;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static void startCrater(ServerLevel level, BlockPos center, Player caster) {
        int cx = center.getX();
        int cz = center.getZ();
        int baseY = center.getY();
        long seed = level.getGameTime() ^ caster.getUUID().getMostSignificantBits();
        level.playSound(null, center, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0F, 0.65F);

        BlockParticleOption sculkShard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SCULK.defaultBlockState());
        BlockParticleOption stoneShard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
        level.sendParticles(ParticleTypes.SONIC_BOOM, cx + 0.5, baseY + 1.0, cz + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(sculkShard, cx + 0.5, baseY + 12.0, cz + 0.5, 2200, 42, 13, 42, 0.72);
        level.sendParticles(stoneShard, cx + 0.5, baseY + 8.0, cz + 0.5, 850, 38, 9, 38, 0.55);
        level.sendParticles(ParticleTypes.SCULK_SOUL, cx + 0.5, baseY + 16.0, cz + 0.5, 900, 40, 22, 40, 0.11);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx + 0.5, baseY + 4.0, cz + 0.5, 650, 35, 8, 35, 0.08);
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, cx + 0.5, baseY + 12 + i * 18, cz + 0.5, 1, 0, 0, 0, 0);
        }

        List<CraterColumn> columns = new ArrayList<>(31_500);
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
            for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > CRATER_RADIUS * CRATER_RADIUS) continue;
                int x = cx + dx;
                int z = cz + dz;
                check.set(x, baseY, z);
                if (!level.hasChunkAt(check)) continue;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                long hash = mix(seed + dx * 341873128712L + dz * 132897987541L);
                double distance = Math.sqrt(distanceSquared);
                double bowl = 1.0 - (distanceSquared / (double) (CRATER_RADIUS * CRATER_RADIUS));
                int roughness = (int) Math.floorMod(hash, 7L) - 3;
                int floor = Math.max(level.getMinY() + 4,
                        surface - (int) Math.round(CRATER_DEPTH * bowl) + roughness);
                columns.add(new CraterColumn(x, z, surface, floor, distance, distanceSquared, hash));
            }
        }
        columns.sort(Comparator.comparingInt(CraterColumn::distanceSquared));
        CRATER_JOBS.add(new CraterJob(level, columns));
    }

    private record CraterColumn(int x, int z, int surface, int floor, double distance, int distanceSquared, long hash) { }

    private static final class CraterJob {
        private final ServerLevel level;
        private final List<CraterColumn> columns;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int index;

        private CraterJob(ServerLevel level, List<CraterColumn> columns) {
            this.level = level;
            this.columns = columns;
        }

        private boolean process(int budget) {
            int end = Math.min(index + budget, columns.size());
            while (index < end) buildColumn(columns.get(index++));
            return index >= columns.size();
        }

        private void buildColumn(CraterColumn column) {
            int x = column.x();
            int z = column.z();
            int floor = column.floor();
            long hash = column.hash();
            pos.set(x, floor, z);
            if (!level.hasChunkAt(pos)) return;

            for (int y = floor + 1; y <= column.surface() && y < level.getMaxY(); y++) {
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(Blocks.BEDROCK)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }

            pos.set(x, floor, z);
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                int material = (int) Math.floorMod(hash >>> 5, 100L);
                level.setBlock(pos, material < 76 ? Blocks.SCULK.defaultBlockState()
                        : material < 91 ? Blocks.DEEPSLATE.defaultBlockState()
                        : Blocks.SOUL_SAND.defaultBlockState(), 3);
            }

            double distance = column.distance();
            int shardChance = distance < 72 ? 4 : 8;
            if (distance < 94 && Math.floorMod(hash >>> 13, shardChance) == 0) {
                int height = 3 + (int) Math.floorMod(hash >>> 21, 19L);
                if (distance > 72) height += (int) ((distance - 72) * 0.55);
                for (int h = 1; h <= height; h++) {
                    int offsetX = h > height * 0.72 && Math.floorMod(hash + h, 3) == 0 ? 1 : 0;
                    pos.set(x + offsetX, floor + h, z);
                    if (pos.getY() >= level.getMaxY()) break;
                    level.setBlock(pos, Math.floorMod(hash + h, 5) == 0
                            ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.SCULK.defaultBlockState(), 3);
                }
                if (Math.floorMod(hash >>> 30, 4) == 0) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x + 0.5, floor + height + 0.5, z + 0.5, 2, 0.3, 0.5, 0.3, 0.02);
                }
            }

            if (distance < 86 && Math.floorMod(hash >>> 37, 31) == 0) {
                pos.set(x, floor + 1, z);
                if (level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.SOUL_FIRE.defaultBlockState(), 3);
            }
        }
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
