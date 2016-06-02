package com.schmois.wurmunlimited.mods.surfaceminingfix;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.classhooks.LocalNameLookup;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import com.schmois.wurmunlimited.mods.surfaceminingfix.items.AzbantiumPickaxe;
import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.deities.Deity;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.SeafloorMiningRig;
import com.wurmonline.server.spells.AzbantiumFistEnchant;
import com.wurmonline.server.spells.Spell;
import com.wurmonline.server.spells.SpellEffect;
import com.wurmonline.server.spells.Spells;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.Descriptor;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

public class SurfaceMiningFixMod
		implements WurmServerMod, Initable, PreInitable, Configurable, ServerStartedListener, ItemTemplatesCreatedListener {

	private static final Logger logger = Logger.getLogger(SurfaceMiningFixMod.class.getName());

	private static float getChance(float power) {
		final float chance = power < 30 ? 25 + (power / 5) : power;
		if (Constants.debug) {
			SurfaceMiningFixMod.logger.log(Level.INFO, "Chance of rock mining: " + chance + "%");
		}
		return chance;
	}

	public static boolean willMineSlope(Creature performer, Item source) {
		if (Constants.alwaysLowerRockSlope)
			return true;
		float power = 0.0F;

		if (Constants.addAzbantiumPickaxeItem && (source.getTemplateId() == Constants.ap_id)) {
			if (!Constants.ap_useQuality)
				return true;
			power = source.getQualityLevel();
			if (Constants.debug) {
				SurfaceMiningFixMod.logger.log(Level.INFO, "Azbantium Pickaxe: " + power + "QL");
			}
		}
		final SpellEffect se = source.getSpellEffect(Constants.af_enchantmentId);
		if (se != null) {
			if (!Constants.af_usePower)
				return true;
			power = se.getPower();
			if (Constants.debug) {
				SurfaceMiningFixMod.logger.log(Level.INFO, "Azbantium Fist: " + power + " power");
			}
		}
		return Server.rand.nextFloat() <= (SurfaceMiningFixMod.getChance(power) / 100);
	}
	
	@Override
	public void configure(Properties properties) {
		Constants.debug = Constants.getBoolean(properties, "debug", Constants.debug);

		Constants.alwaysLowerRockSlope = Constants.getBoolean(properties, "alwaysLowerRockSlope",
				Constants.alwaysLowerRockSlope);
		Constants.noNeedToUnconverRock = Constants.getBoolean(properties, "noNeedToUnconverRock",
				Constants.noNeedToUnconverRock);

		// Azbantium Fist
		Constants.addAzbantiumFistEnchantment = Constants.getBoolean(properties, "addAzbantiumFistEnchantment",
				Constants.addAzbantiumFistEnchantment);

		Constants.af_enchantmentId = Byte.valueOf(
				properties.getProperty("af_enchantmentId", Byte.toString(Constants.af_enchantmentId)).replace(",", ""));

		Constants.af_spellCost = Integer.valueOf(
				properties.getProperty("af_spellCost", Integer.toString(Constants.af_spellCost)).replace(",", ""));
		Constants.af_spellDifficulty = Integer.valueOf(properties
				.getProperty("af_spellDifficulty", Integer.toString(Constants.af_spellDifficulty)).replace(",", ""));
		Constants.af_spellCooldown = Long.valueOf(
				properties.getProperty("af_spellCooldown", Long.toString(Constants.af_spellCooldown)).replace(",", ""));

		Constants.af_all = Constants.getBoolean(properties, "af_all", Constants.af_all);
		Constants.af_fo = Constants.getBoolean(properties, "af_fo", Constants.af_fo);
		Constants.af_magranon = Constants.getBoolean(properties, "af_magranon", Constants.af_magranon);
		Constants.af_vynora = Constants.getBoolean(properties, "af_vynora", Constants.af_vynora);
		Constants.af_xiax = Constants.getBoolean(properties, "af_xiax", Constants.af_xiax);

		Constants.af_ironMaterial = Constants.getBoolean(properties, "af_ironMaterial", Constants.af_ironMaterial);
		Constants.af_steelMaterial = Constants.getBoolean(properties, "af_steelMaterial", Constants.af_steelMaterial);
		Constants.af_seryllMaterial = Constants.getBoolean(properties, "af_seryllMaterial",
				Constants.af_seryllMaterial);
		Constants.af_glimmersteelMaterial = Constants.getBoolean(properties, "af_glimmersteelMaterial",
				Constants.af_glimmersteelMaterial);
		Constants.af_adamantineMaterial = Constants.getBoolean(properties, "af_adamantineMaterial",
				Constants.af_adamantineMaterial);

		Constants.af_usePower = Constants.getBoolean(properties, "af_usePower", Constants.af_usePower);

		Constants.af_allowWoA = Constants.getBoolean(properties, "af_allowWoA", Constants.af_allowWoA);
		
		Constants.af_allowBotD = Constants.getBoolean(properties, "af_allowBotD", Constants.af_allowBotD);

		// Azbantium Pickaxe
		Constants.addAzbantiumPickaxeItem = Constants.getBoolean(properties, "addAzbantiumPickaxeItem",
				Constants.addAzbantiumPickaxeItem);

		Constants.ap_decayTime = Long.valueOf(
				properties.getProperty("ap_decayTime", Long.toString(Constants.ap_decayTime)).replace(",", ""));
		Constants.ap_difficulty = Float.valueOf(
				properties.getProperty("ap_difficulty", Float.toString(Constants.ap_difficulty)).replace(",", ""));
		Constants.ap_weight = Integer
				.valueOf(properties.getProperty("ap_weight", Integer.toString(Constants.ap_weight)).replace(",", ""));

		Constants.ap_useQuality = Constants.getBoolean(properties, "ap_useQuality", Constants.ap_useQuality);

		// Seafloor Mining Rig
		Constants.addSeafloorMiningRigItem = Constants.getBoolean(properties, "addSeafloorMiningRigItem",
				Constants.addSeafloorMiningRigItem);

		Constants.smr_decayTime = Long.valueOf(
				properties.getProperty("smr_decayTime", Long.toString(Constants.smr_decayTime)).replace(",", ""));
		Constants.smr_difficulty = Float.valueOf(
				properties.getProperty("smr_difficulty", Float.toString(Constants.smr_difficulty)).replace(",", ""));
		Constants.smr_weight = Integer
				.valueOf(properties.getProperty("smr_weight", Integer.toString(Constants.smr_weight)).replace(",", ""));

		if (Constants.debug) {
			SurfaceMiningFixMod.logger.log(Level.INFO, "debug: " + Constants.debug);

			SurfaceMiningFixMod.logger.log(Level.INFO, "alwaysLowerRockSlope: " + Constants.alwaysLowerRockSlope);
			SurfaceMiningFixMod.logger.log(Level.INFO, "noNeedToUnconverRock: " + Constants.noNeedToUnconverRock);

			// Azbantium Fist
			SurfaceMiningFixMod.logger.log(Level.INFO,
					"addAzbantiumFistEnchantment: " + Constants.addAzbantiumFistEnchantment);

			SurfaceMiningFixMod.logger.log(Level.INFO, "af_enchantmentId: " + Constants.af_enchantmentId);

			SurfaceMiningFixMod.logger.log(Level.INFO, "af_spellCost: " + Constants.af_spellCost);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_spellDifficulty: " + Constants.af_spellDifficulty);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_spellCooldown: " + Constants.af_spellCooldown);

			SurfaceMiningFixMod.logger.log(Level.INFO, "af_all: " + Constants.af_all);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_fo: " + Constants.af_fo);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_magranon: " + Constants.af_magranon);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_vynora: " + Constants.af_vynora);

			SurfaceMiningFixMod.logger.log(Level.INFO, "af_ironMaterial: " + Constants.af_ironMaterial);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_steelMaterial: " + Constants.af_steelMaterial);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_seryllMaterial: " + Constants.af_seryllMaterial);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_glimmersteelMaterial: " + Constants.af_glimmersteelMaterial);
			SurfaceMiningFixMod.logger.log(Level.INFO, "af_adamantineMaterial: " + Constants.af_adamantineMaterial);

			SurfaceMiningFixMod.logger.log(Level.INFO, "af_usePower: " + Constants.af_usePower);

			SurfaceMiningFixMod.logger.log(Level.INFO, "af_allowWoA: " + Constants.af_allowWoA);

			// Azbantium Pickaxe Item
			SurfaceMiningFixMod.logger.log(Level.INFO, "addAzbantiumPickaxeItem: " + Constants.addAzbantiumPickaxeItem);

			SurfaceMiningFixMod.logger.log(Level.INFO, "ap_decayTime: " + Constants.ap_decayTime);
			SurfaceMiningFixMod.logger.log(Level.INFO, "ap_difficulty: " + Constants.ap_difficulty);
			SurfaceMiningFixMod.logger.log(Level.INFO, "ap_weight: " + Constants.ap_weight);

			SurfaceMiningFixMod.logger.log(Level.INFO, "ap_useQuality: " + Constants.ap_useQuality);

			// Seafloor Mining Rig
			SurfaceMiningFixMod.logger.log(Level.INFO,
					"addSeafloorMiningRigItem: " + Constants.addSeafloorMiningRigItem);

			SurfaceMiningFixMod.logger.log(Level.INFO, "smr_decayTime: " + Constants.smr_decayTime);
			SurfaceMiningFixMod.logger.log(Level.INFO, "smr_difficulty: " + Constants.smr_difficulty);
			SurfaceMiningFixMod.logger.log(Level.INFO, "smr_weight: " + Constants.smr_weight);
		}
	}

	@Override
	public void init() {
		if (!Constants.af_allowWoA || !Constants.af_allowBotD) {
			final InvocationHandlerFactory spellCheck = () -> (proxy, method, args) -> {
				final Object precondition = method.invoke(proxy, args);
				if ((precondition instanceof Boolean) && (proxy instanceof Spell)) {
					if (!(boolean) precondition)
						return false;
					final Creature performer = (Creature) args[1];
					final Item target = (Item) args[2];
					if (target.getSpellEffect(Constants.af_enchantmentId) != null) {
						performer.getCommunicator().sendNormalServerMessage("The " + target.getName()
								+ " is already enchanted with something that would negate the effect.");
						return false;
					}
				}
				return precondition;
			};

			if (!Constants.af_allowWoA) {
				try {

					final ClassPool classPool = HookManager.getInstance().getClassPool();
					final CtClass[] paramTypes = { classPool.get("com.wurmonline.server.skills.Skill"),
							classPool.get("com.wurmonline.server.creatures.Creature"),
							classPool.get("com.wurmonline.server.items.Item") };
					HookManager.getInstance().registerHook("com.wurmonline.server.spells.WindOfAges", "precondition",
							Descriptor.ofMethod(CtClass.booleanType, paramTypes), spellCheck);
				} catch (final NotFoundException e) {
					SurfaceMiningFixMod.logger.log(Level.INFO, "Broken Wind of Ages hook, let dev know", e);
				}
			}

			if (!Constants.af_allowBotD) {
				try {

					final ClassPool classPool = HookManager.getInstance().getClassPool();
					final CtClass[] paramTypes = { classPool.get("com.wurmonline.server.skills.Skill"),
							classPool.get("com.wurmonline.server.creatures.Creature"),
							classPool.get("com.wurmonline.server.items.Item") };
					HookManager.getInstance().registerHook("com.wurmonline.server.spells.BlessingDark", "precondition",
							Descriptor.ofMethod(CtClass.booleanType, paramTypes), spellCheck);
				} catch (final NotFoundException e) {
					SurfaceMiningFixMod.logger.log(Level.INFO, "Broken Blessing of the Dark hook, let dev know", e);
				}
			}
		}
	}

	@Override
	public void onItemTemplatesCreated() {
		if (Constants.addAzbantiumPickaxeItem) {
			new AzbantiumPickaxe();
		}
		if (Constants.addSeafloorMiningRigItem) {
			new SeafloorMiningRig();
		}
	}

	@Override
	public void onServerStarted() {
		new Runnable() {
			@Override
			public void run() {
				if (Constants.addAzbantiumFistEnchantment) {
					SurfaceMiningFixMod.logger.log(Level.INFO, "Registering AzbantiumFist enchant");

					final AzbantiumFistEnchant azbantiumPickaxe = new AzbantiumFistEnchant(Constants.af_spellCost,
							Constants.af_spellDifficulty, Constants.af_spellCooldown);

					try {
						ReflectionUtil.callPrivateMethod(Spells.class,
								ReflectionUtil.getMethod(Spells.class, "addSpell"), azbantiumPickaxe);
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
							| NoSuchMethodException e) {
						throw new RuntimeException(e);
					}

					if (Constants.af_all) {
						for (final Deity deity : Deities.getDeities()) {
							deity.addSpell(azbantiumPickaxe);
						}
					} else {
						if (Constants.af_fo) {
							Deities.getDeity(Deities.DEITY_FO).addSpell(azbantiumPickaxe);
						}

						if (Constants.af_magranon) {
							Deities.getDeity(Deities.DEITY_MAGRANON).addSpell(azbantiumPickaxe);
						}

						if (Constants.af_vynora) {
							Deities.getDeity(Deities.DEITY_VYNORA).addSpell(azbantiumPickaxe);
						}

						if (Constants.af_libila) {
							Deities.getDeity(Deities.DEITY_LIBILA).addSpell(azbantiumPickaxe);
						}
						if (Constants.af_xiax) {
							Deities.getDeity(104).addSpell(azbantiumPickaxe);
						}
					}
				}
			}
		}.run();

	}

	@Override
	public void preInit() {
		ModActions.init();
		if (Constants.alwaysLowerRockSlope || Constants.addAzbantiumFistEnchantment
				|| Constants.addSeafloorMiningRigItem) {
			try {
				/**
				 * The condition for checking if it mined a slope or not is
				 * repeated so we need to run the following code twice, it's
				 * really dumb.
				 */
				replaceMineSlopeCondition();
				replaceMineSlopeCondition();
			} catch (NotFoundException | BadBytecode e) {
				throw new HookException(e);
			}
		}
		if (Constants.noNeedToUnconverRock) {
			try {
				replaceSurroundingRockCondition();
			} catch (NotFoundException | BadBytecode e) {
				throw new HookException(e);
			}
		}
		if (Constants.addSeafloorMiningRigItem) {
			try {
				SeafloorMiningRig.replaceWaterHeightCondition();
			} catch (NotFoundException | BadBytecode e) {
				throw new HookException(e);
			}
		}
	}

	private void replaceMineSlopeCondition() throws NotFoundException, BadBytecode {
		final ClassPool classPool = HookManager.getInstance().getClassPool();
		final CtClass ctTileRockBehaviour = classPool.get("com.wurmonline.server.behaviours.TileRockBehaviour");
		final CtClass ctRandom = classPool.get("java.util.Random");
		final CtClass ctServer = classPool.get("com.wurmonline.server.Server");
		final CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
		final CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
		final CtClass ctAction = classPool.get("com.wurmonline.server.behaviours.Action");
				
		ctTileRockBehaviour.getClassFile().compact();

		final CtClass[] paramTypes = { ctAction, ctCreature, ctItem, CtClass.intType, CtClass.intType,
				CtClass.booleanType, CtClass.intType, CtClass.intType, CtClass.shortType, CtClass.floatType };

		final CtMethod method = ctTileRockBehaviour.getMethod("action",
				Descriptor.ofMethod(CtClass.booleanType, paramTypes));

		final MethodInfo methodInfo = method.getMethodInfo();
		final CodeAttribute codeAttribute = methodInfo.getCodeAttribute();

		final LocalNameLookup localNames = new LocalNameLookup(
				(LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag));

		Bytecode bytecode = new Bytecode(methodInfo.getConstPool());
		bytecode.addGetstatic(ctServer, "rand", Descriptor.of(ctRandom));
		bytecode.addIconst(5);
		bytecode.addInvokevirtual(ctRandom, "nextInt",
				Descriptor.ofMethod(CtClass.intType, new CtClass[] { CtClass.intType }));
		bytecode.add(Opcode.IFNE);
		final byte[] search = bytecode.get();

		bytecode = new Bytecode(methodInfo.getConstPool());
		bytecode.addAload(localNames.get("performer"));
		bytecode.addAload(localNames.get("source"));
		bytecode.addInvokestatic(classPool.get(this.getClass().getName()), "willMineSlope",
				Descriptor.ofMethod(CtClass.booleanType, new CtClass[] { ctCreature, ctItem }));
		bytecode.addGap(2);
		bytecode.add(Opcode.IFEQ);
		final byte[] replacement = bytecode.get();

		new CodeReplacer(codeAttribute).replaceCode(search, replacement);
		methodInfo.rebuildStackMap(classPool);
	}

	/*
	 * @formatter:off L766 1665 aload_2; performer 1666 invokevirtual 272;
	 * com.wurmonline.server.creatures.Communicator getCommunicator() L767 1669
	 * ldc_w 916; "The surrounding area needs to be rock before you mine." L766
	 * 1672 invokevirtual 278; void sendNormalServerMessage(java.lang.String
	 * performer) L768 1675 iconst_1; 1676 ireturn;
	 * 
	 * @formatter:on
	 */
	private void replaceSurroundingRockCondition() throws NotFoundException, BadBytecode {
		final ClassPool classPool = HookManager.getInstance().getClassPool();
		final CtClass ctString = classPool.get("java.lang.String");
		final CtClass ctTileRockBehaviour = classPool.get("com.wurmonline.server.behaviours.TileRockBehaviour");
		final CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
		final CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");
		final CtClass ctCommunicator = classPool.get("com.wurmonline.server.creatures.Communicator");
		
		ctTileRockBehaviour.getClassFile().compact();

		final CtClass[] paramTypes = { classPool.get("com.wurmonline.server.behaviours.Action"), ctCreature, ctItem,
				CtClass.intType, CtClass.intType, CtClass.booleanType, CtClass.intType, CtClass.intType,
				CtClass.shortType, CtClass.floatType };

		final CtMethod method = ctTileRockBehaviour.getMethod("action",
				Descriptor.ofMethod(CtClass.booleanType, paramTypes));

		final MethodInfo methodInfo = method.getMethodInfo();
		final CodeAttribute codeAttribute = methodInfo.getCodeAttribute();

		Bytecode bytecode = new Bytecode(methodInfo.getConstPool());
		bytecode.addAload(2);
		bytecode.addInvokevirtual(ctCreature, "getCommunicator", ctCommunicator, new CtClass[] {});

		bytecode.addLdc("The surrounding area needs to be rock before you mine.");

		bytecode.addInvokevirtual(ctCommunicator, "sendNormalServerMessage", CtClass.voidType,
				new CtClass[] { ctString });

		bytecode.addIconst(1);
		bytecode.add(Opcode.IRETURN);

		final byte[] search = bytecode.get();

		bytecode = new Bytecode(methodInfo.getConstPool());
		bytecode.addGap(search.length);
		final byte[] replacement = bytecode.get();

		new CodeReplacer(codeAttribute).replaceCode(search, replacement);
		methodInfo.rebuildStackMap(classPool);
	}

}
