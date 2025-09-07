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
	private final List<IGenerator> queue;
	
	public SpawnerWorld(World world) {
		this.world = world;
		this.spawners = Collections.synchronizedMap(new HashMap<>());
		this.queue = Collections.synchronizedList(new LinkedList<>());
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
                                       queue.add(new ActiveGenerator(ISpawner.of(block)));
                               }
                       }
               }
       }

       public void unload(Chunk chunk) {
               synchronized (spawners) {
                       Iterator<IGenerator> it = spawners.values().iterator();
                       while(it.hasNext()) {
                               IGenerator g = it.next();
                               if(g.in(chunk)) {
                                       g.remove(false);
                                       it.remove();
                               }
                       }
               }
       }

	public void clear() {
		spawners.values().forEach(IGenerator::clear);
		spawners.clear();
	}
	
	public int active() {
		return spawners.size();
	}
	
	public void update() {
		spawners.values().forEach(IGenerator::update);
	}
	
	public void control() {
		spawners.values().forEach(IGenerator::control);
	}
	
       public void tick() {
               if(queue.isEmpty() == false) {
                       synchronized (queue) {
                               for(IGenerator g : queue) {
                                       put(g);
                               }
                               queue.clear();
                       }
               }
               for(IGenerator g : spawners.values()) {
                       g.tick();
               }
       }

       public void reduce() {
               synchronized (spawners) {
                       Iterator<Map.Entry<Pos, IGenerator>> it = spawners.entrySet().iterator();
                       while(it.hasNext()) {
                               Map.Entry<Pos, IGenerator> e = it.next();
                               IGenerator g = e.getValue();
                               if(!g.active() || !g.present()) {
                                       g.clear();
                                       it.remove();
                               }
                       }
               }
       }

       public int remove(boolean fully, Predicate<IGenerator> filter) {
               int removed = 0;
               synchronized (spawners) {
                       Iterator<Map.Entry<Pos, IGenerator>> it = spawners.entrySet().iterator();
                       while(it.hasNext()) {
                               Map.Entry<Pos, IGenerator> e = it.next();
                               IGenerator g = e.getValue();
                               if(g.active() && filter.test(g)) {
                                       g.remove(fully);
                                       it.remove();
                                       removed++;
                               }
                       }
               }
               return removed;
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
