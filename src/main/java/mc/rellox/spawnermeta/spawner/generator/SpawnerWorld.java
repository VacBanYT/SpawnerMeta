package mc.rellox.spawnermeta.spawner.generator;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
	
	public final World world;
        protected final Map<Pos, IGenerator> spawners;
        private final Deque<Block> queue;
        private static final int LOAD_BATCH = 32;
	
	public SpawnerWorld(World world) {
		this.world = world;
                this.spawners = Collections.synchronizedMap(new HashMap<>());
                this.queue = new ArrayDeque<>();
	}
	
	public Stream<IGenerator> stream() {
		return spawners.values().stream();
	}
	
        public void load() {
                for(Chunk chunk : world.getLoadedChunks()) {
                        load(chunk);
                }
        }

        public void load(Chunk chunk) {
                for(BlockState state : chunk.getTileEntities()) {
                        if(state instanceof CreatureSpawner) {
                                Block block = state.getBlock();
                                if(Settings.settings.ignored(block) == false) {
                                        queue.add(block);
                                }
                        }
                }
        }

        public void unload(Chunk chunk) {
                synchronized (spawners) {
                        Iterator<Map.Entry<Pos, IGenerator>> it = spawners.entrySet().iterator();
                        while(it.hasNext()) {
                                Map.Entry<Pos, IGenerator> entry = it.next();
                                IGenerator g = entry.getValue();
                                if(g.in(chunk)) {
                                        g.remove(false);
                                        it.remove();
                                }
                        }
                }
                queue.removeIf(block -> block.getWorld() == chunk.getWorld()
                                && (block.getX() >> 4) == chunk.getX()
                                && (block.getZ() >> 4) == chunk.getZ());
        }

        public void clear() {
                spawners.values().forEach(IGenerator::clear);
                spawners.clear();
                queue.clear();
        }
	
	public int active() {
		return spawners.size();
	}
	
        public void update() {
                for(IGenerator g : spawners.values()) g.update();
        }

        public void control() {
                for(IGenerator g : spawners.values()) g.control();
        }
	
        public void tick() {
                int processed = 0;
                while(processed++ < LOAD_BATCH) {
                        Block block = queue.poll();
                        if(block == null) break;
                        if(block.getType() != Material.SPAWNER) continue;
                        put(block);
                }
                for(IGenerator g : spawners.values()) g.tick();
        }

	public void reduce() {
		List<Pos> toRemove = new ArrayList<>();

		Map<Pos, IGenerator> spawnersCopy;
		synchronized (spawners) {
			spawnersCopy = new HashMap<>(spawners);
		}

		spawnersCopy.forEach((pos, generator) -> {
			if (!generator.active() || !generator.present()) {
				generator.clear();
				toRemove.add(pos);
			}
		});

		synchronized (spawners) {
			toRemove.forEach(spawners::remove);
		}
	}

	public int remove(boolean fully, Predicate<IGenerator> filter) {
		List<Pos> toRemove = new ArrayList<>();

		Map<Pos, IGenerator> spawnersCopy;
		synchronized (spawners) {
			spawnersCopy = new HashMap<>(spawners);
		}

		spawnersCopy.forEach((pos, generator) -> {
			if (generator.active() && filter.test(generator)) {
				generator.remove(fully);
				toRemove.add(pos);
			}
		});

		synchronized (spawners) {
			toRemove.forEach(spawners::remove);
		}

		return toRemove.size();
	}
	
	public void put(Block block) {
		put(new ActiveGenerator(ISpawner.of(block)));
	}
	
	private void put(IGenerator generator) {
		IGenerator last = spawners.put(generator.position(), generator);
		if(last != null) last.clear();
	}
	
	public IGenerator get(Block block) {
		IGenerator generator = spawners.get(Pos.of(block));
		if(generator == null) {
			if(block.getType() == Material.SPAWNER) put(block);
		} else if(generator.active() == false) return null;
		return generator;
	}
	
	public IGenerator raw(Block block) {
		return spawners.get(Pos.of(block));
	}

}
