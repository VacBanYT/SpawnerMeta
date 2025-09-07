package mc.rellox.spawnermeta.spawner.generator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;

import mc.rellox.spawnermeta.api.spawner.IGenerator;
import mc.rellox.spawnermeta.api.spawner.ISpawner;
import mc.rellox.spawnermeta.api.spawner.location.Pos;
import mc.rellox.spawnermeta.configuration.Settings;
import mc.rellox.spawnermeta.spawner.ActiveGenerator;

public class SpawnerWorld {
	
        private static final int LOADS_PER_TICK = 64;

        public final World world;
        protected final Map<Pos, IGenerator> spawners;
        private final Queue<IGenerator> queue;

        public SpawnerWorld(World world) {
                this.world = world;
                this.spawners = new ConcurrentHashMap<>();
                this.queue = new ConcurrentLinkedQueue<>();
        }

        public java.util.stream.Stream<IGenerator> stream() {
                return spawners.values().stream();
        }

        public void load() {
                for (Chunk chunk : world.getLoadedChunks()) {
                        load(chunk);
                }
        }

        public void load(Chunk chunk) {
                for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof CreatureSpawner) {
                                Block block = state.getBlock();
                                if (!Settings.settings.ignored(block)) {
                                        queue.add(new ActiveGenerator(ISpawner.of(block)));
                                }
                        }
                }
        }

        public void unload(Chunk chunk) {
                Iterator<Map.Entry<Pos, IGenerator>> it = spawners.entrySet().iterator();
                while (it.hasNext()) {
                        Map.Entry<Pos, IGenerator> entry = it.next();
                        IGenerator g = entry.getValue();
                        if (g.in(chunk)) {
                                g.remove(false);
                                it.remove();
                        }
                }
        }

        public void clear() {
                for (IGenerator g : spawners.values()) {
                        g.clear();
                }
                spawners.clear();
                queue.clear();
        }

        public int active() {
                return spawners.size();
        }

        public void update() {
                for (IGenerator g : spawners.values()) {
                        g.update();
                }
        }

        public void control() {
                for (IGenerator g : spawners.values()) {
                        g.control();
                }
        }

        public void tick() {
                int processed = 0;
                IGenerator gen;
                while (processed < LOADS_PER_TICK && (gen = queue.poll()) != null) {
                        put(gen);
                        processed++;
                }
                for (IGenerator g : spawners.values()) {
                        g.tick();
                }
        }

        public void reduce() {
                Iterator<Map.Entry<Pos, IGenerator>> it = spawners.entrySet().iterator();
                while (it.hasNext()) {
                        Map.Entry<Pos, IGenerator> entry = it.next();
                        IGenerator generator = entry.getValue();
                        if (!generator.active() || !generator.present()) {
                                generator.clear();
                                it.remove();
                        }
                }
        }

        public int remove(boolean fully, Predicate<IGenerator> filter) {
                int removed = 0;
                Iterator<Map.Entry<Pos, IGenerator>> it = spawners.entrySet().iterator();
                while (it.hasNext()) {
                        Map.Entry<Pos, IGenerator> entry = it.next();
                        IGenerator generator = entry.getValue();
                        if (generator.active() && filter.test(generator)) {
                                generator.remove(fully);
                                it.remove();
                                removed++;
                        }
                }
                return removed;
        }

        public void put(Block block) {
                put(new ActiveGenerator(ISpawner.of(block)));
        }

        private void put(IGenerator generator) {
                IGenerator last = spawners.put(generator.position(), generator);
                if (last != null)
                        last.clear();
        }

        public IGenerator get(Block block) {
                IGenerator generator = spawners.get(Pos.of(block));
                if (generator == null) {
                        if (block.getType() == Material.SPAWNER)
                                put(block);
                } else if (generator.active() == false)
                        return null;
                return generator;
        }

        public IGenerator raw(Block block) {
                return spawners.get(Pos.of(block));
        }

}
