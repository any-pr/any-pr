package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class BiomeGenBase {
	public static final BiomeGenBase[] field_35521_a = new BiomeGenBase[256];
	public static final BiomeGenBase field_35519_b = (new BiomeGenOcean(0)).setColor(112).setBiomeName("Ocean").func_35511_b(-1.0F, 0.5F);
	public static final BiomeGenBase field_35520_c = (new BiomeGenPlains(1)).setColor(9286496).setBiomeName("Plains").func_35512_a(0.8F, 0.4F);
	public static final BiomeGenBase desert = (new BiomeGenDesert(2)).setColor(16421912).setBiomeName("Desert").setDisableRain().func_35512_a(2.0F, 0.0F).func_35511_b(0.1F, 0.2F);
	public static final BiomeGenBase field_35518_e = (new BiomeGenHills(3)).setColor(6316128).setBiomeName("Extreme Hills").func_35511_b(0.2F, 1.8F).func_35512_a(0.2F, 0.3F);
	public static final BiomeGenBase forest = (new BiomeGenForest(4)).setColor(353825).setBiomeName("Forest").func_4080_a(5159473).func_35512_a(0.7F, 0.8F);
	public static final BiomeGenBase taiga = (new BiomeGenTaiga(5)).setColor(747097).setBiomeName("Taiga").func_4080_a(5159473).func_35512_a(0.3F, 0.8F).func_35511_b(0.1F, 0.4F);
	public static final BiomeGenBase swampland = (new BiomeGenSwamp(6)).setColor(522674).setBiomeName("Swampland").func_4080_a(9154376).func_35511_b(-0.2F, 0.1F).func_35512_a(0.8F, 0.9F);
	public static final BiomeGenBase field_35522_i = (new BiomeGenRiver(7)).setColor(255).setBiomeName("River").func_35511_b(-0.5F, 0.0F);
	public static final BiomeGenBase hell = (new BiomeGenHell(8)).setColor(16711680).setBiomeName("Hell").setDisableRain();
	public static final BiomeGenBase sky = (new BiomeGenSky(9)).setColor(8421631).setBiomeName("Sky").setDisableRain();
	public String biomeName;
	public int color;
	public byte topBlock = (byte)Block.grass.blockID;
	public byte fillerBlock = (byte)Block.dirt.blockID;
	public int field_6161_q = 5169201;
	public float field_35527_q = 0.1F;
	public float field_35526_r = 0.3F;
	public float field_35525_s = 0.5F;
	public float field_35524_t = 0.5F;
	public BiomeDecorator field_35523_u;
	protected List spawnableMonsterList = new ArrayList();
	protected List spawnableCreatureList = new ArrayList();
	protected List spawnableWaterCreatureList = new ArrayList();
	private boolean enableSnow;
	private boolean enableRain = true;
	public final int field_35529_y;
	protected WorldGenTrees field_35528_z = new WorldGenTrees();
	protected WorldGenBigTree field_35515_A = new WorldGenBigTree();
	protected WorldGenForest field_35516_B = new WorldGenForest();
	protected WorldGenSwamp field_35517_C = new WorldGenSwamp();

	protected BiomeGenBase(int var1) {
		this.field_35529_y = var1;
		field_35521_a[var1] = this;
		this.field_35523_u = this.func_35514_a();
		this.spawnableCreatureList.add(new SpawnListEntry(EntitySheep.class, 12, 4, 4));
		this.spawnableCreatureList.add(new SpawnListEntry(EntityPig.class, 10, 4, 4));
		this.spawnableCreatureList.add(new SpawnListEntry(EntityChicken.class, 10, 4, 4));
		this.spawnableCreatureList.add(new SpawnListEntry(EntityCow.class, 8, 4, 4));
		this.spawnableMonsterList.add(new SpawnListEntry(EntitySpider.class, 10, 4, 4));
		this.spawnableMonsterList.add(new SpawnListEntry(EntityZombie.class, 10, 4, 4));
		this.spawnableMonsterList.add(new SpawnListEntry(EntitySkeleton.class, 10, 4, 4));
		this.spawnableMonsterList.add(new SpawnListEntry(EntityCreeper.class, 10, 4, 4));
		this.spawnableMonsterList.add(new SpawnListEntry(EntitySlime.class, 10, 4, 4));
		this.spawnableMonsterList.add(new SpawnListEntry(EntityEnderman.class, 2, 4, 4));
		this.spawnableWaterCreatureList.add(new SpawnListEntry(EntitySquid.class, 10, 4, 4));
	}

	protected BiomeDecorator func_35514_a() {
		return new BiomeDecorator(this);
	}

	private BiomeGenBase func_35512_a(float var1, float var2) {
		this.field_35525_s = var1;
		this.field_35524_t = var2;
		return this;
	}

	private BiomeGenBase func_35511_b(float var1, float var2) {
		this.field_35527_q = var1;
		this.field_35526_r = var2;
		return this;
	}

	private BiomeGenBase setDisableRain() {
		this.enableRain = false;
		return this;
	}

	public WorldGenerator getRandomWorldGenForTrees(Random var1) {
		return (WorldGenerator)(var1.nextInt(10) == 0 ? this.field_35515_A : this.field_35528_z);
	}

	protected BiomeGenBase setBiomeName(String var1) {
		this.biomeName = var1;
		return this;
	}

	protected BiomeGenBase func_4080_a(int var1) {
		this.field_6161_q = var1;
		return this;
	}

	protected BiomeGenBase setColor(int var1) {
		this.color = var1;
		return this;
	}

	public List getSpawnableList(EnumCreatureType var1) {
		return var1 == EnumCreatureType.monster ? this.spawnableMonsterList : (var1 == EnumCreatureType.creature ? this.spawnableCreatureList : (var1 == EnumCreatureType.waterCreature ? this.spawnableWaterCreatureList : null));
	}

	public boolean getEnableSnow() {
		return this.enableSnow;
	}

	public boolean canSpawnLightningBolt() {
		return this.enableSnow ? false : this.enableRain;
	}

	public float getBiome() {
		return 0.1F;
	}

	public final int func_35510_e() {
		return (int)(this.field_35524_t * 65536.0F);
	}

	public final int func_35509_f() {
		return (int)(this.field_35525_s * 65536.0F);
	}

	public void func_35513_a(World var1, Random var2, int var3, int var4) {
		this.field_35523_u.func_35255_a(var1, var2, var3, var4);
	}
}
