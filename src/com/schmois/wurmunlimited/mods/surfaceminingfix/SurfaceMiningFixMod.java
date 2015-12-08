package com.schmois.wurmunlimited.mods.surfaceminingfix;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.LocalNameLookup;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import com.wurmonline.server.Server;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.deities.Deities;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.spells.AzbantiumFistEnchant;
import com.wurmonline.server.spells.SpellEffect;
import com.wurmonline.server.spells.Spells;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.Descriptor;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;

public class SurfaceMiningFixMod implements WurmMod, Initable, PreInitable, Configurable, ServerStartedListener {

    private boolean addSpell = true;
    // private boolean addItem = false;
    private static boolean debug = false;
    private int spellCost = 50;
    private int spellDifficulty = 60;
    private long spellCooldown = 0L;
    private static final Logger logger = Logger.getLogger(SurfaceMiningFixMod.class.getName());

    @Override
    public void onServerStarted() {
        new Runnable() {
            @Override
            public void run() {
                if (addSpell) {
                    logger.log(Level.INFO, "Registering AzbantiumFist enchant");

                    AzbantiumFistEnchant azbantiumPickaxe = new AzbantiumFistEnchant(spellCost, spellDifficulty,
                            spellCooldown);

                    try {
                        ReflectionUtil.callPrivateMethod(Spells.class,
                                ReflectionUtil.getMethod(Spells.class, "addSpell"), azbantiumPickaxe);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                            | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }

                    Deities.getDeity(Deities.DEITY_MAGRANON).addSpell(azbantiumPickaxe);
                }
            }
        }.run();

    }

    @Override
    public void configure(Properties properties) {
        String spell = properties.getProperty("addSpell", Boolean.toString(addSpell));
        // TODO: String item = properties.getProperty("addItem", Boolean.toString(addItem));
        String debugValue = properties.getProperty("debug", Boolean.toString(debug));
        addSpell = spell.equalsIgnoreCase("true") || spell.equalsIgnoreCase("yes") || spell.equalsIgnoreCase("1");
        // TODO: addItem = item.equalsIgnoreCase("true") || item.equalsIgnoreCase("yes") || item.equalsIgnoreCase("1");
        spellCost = Integer.valueOf(properties.getProperty("spellCost", Integer.toString(spellCost)));
        spellDifficulty = Integer.valueOf(properties.getProperty("spellDifficulty", Integer.toString(spellDifficulty)));
        spellCooldown = Long.valueOf(properties.getProperty("spellCooldown", Long.toString(spellCooldown)));

        debug = debugValue.equalsIgnoreCase("true") || debugValue.equalsIgnoreCase("yes")
                || debugValue.equalsIgnoreCase("1");

        if (debug) {
            logger.log(Level.INFO, "addSpell: " + addSpell);
            // TODO: logger.log(Level.INFO, "addItem: " + addItem);
            logger.log(Level.INFO, "spellCost: " + spellCost);
            logger.log(Level.INFO, "spellDifficulty: " + spellDifficulty);
            logger.log(Level.INFO, "spellCooldown: " + spellCooldown);
            logger.log(Level.INFO, "debug: " + debug);
        }
    }

    @Override
    public void preInit() {
        ModActions.init();
        try {
            /**
             * The condition for checking if it mined a slope or not is repeated so we need to run the following code twice, it's really dumb.
             */
            replaceMineSlopeCondition();
            replaceMineSlopeCondition(); // Stupid Rolf code
        } catch (NotFoundException | BadBytecode e) {
            throw new HookException(e);
        }
    }

    @Override
    public void init() {
    }

    public static boolean willMineSlope(Creature performer, Item source) {
        SpellEffect se = source.getSpellEffect(AzbantiumFistEnchant.BUFF_AZBANTIUM_FIST);
        if (se != null) {
            float power = se.getPower();
            float chance = power < 30 ? 25 + power / 5 : power;
            if (debug) {
                logger.log(Level.INFO, "Chance of rock mining: " + chance);
            }
            return Server.rand.nextFloat() <= chance / 100;
        }
        return Server.rand.nextInt(5) == 0;
    }

    private void replaceMineSlopeCondition() throws NotFoundException, BadBytecode {
        ClassPool classPool = HookManager.getInstance().getClassPool();
        CtClass ctTileRockBehaviour = classPool.get("com.wurmonline.server.behaviours.TileRockBehaviour");
        CtClass ctRandom = classPool.get("java.util.Random");
        CtClass ctServer = classPool.get("com.wurmonline.server.Server");
        CtClass ctCreature = classPool.get("com.wurmonline.server.creatures.Creature");
        CtClass ctItem = classPool.get("com.wurmonline.server.items.Item");

        CtClass[] paramTypes = { classPool.get("com.wurmonline.server.behaviours.Action"),
                classPool.get("com.wurmonline.server.creatures.Creature"),
                classPool.get("com.wurmonline.server.items.Item"), CtPrimitiveType.intType, CtPrimitiveType.intType,
                CtPrimitiveType.booleanType, CtPrimitiveType.intType, CtPrimitiveType.intType,
                CtPrimitiveType.shortType, CtPrimitiveType.floatType };

        CtMethod method = ctTileRockBehaviour.getMethod("action",
                Descriptor.ofMethod(CtPrimitiveType.booleanType, paramTypes));

        MethodInfo methodInfo = method.getMethodInfo();
        CodeAttribute codeAttribute = methodInfo.getCodeAttribute();

        LocalNameLookup localNames = new LocalNameLookup(
                (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag));

        Bytecode bytecode = new Bytecode(methodInfo.getConstPool());
        bytecode.addGetstatic(ctServer, "rand", Descriptor.of(ctRandom));
        bytecode.addIconst(5);
        bytecode.addInvokevirtual(ctRandom, "nextInt",
                Descriptor.ofMethod(CtPrimitiveType.intType, new CtClass[] { CtPrimitiveType.intType }));
        bytecode.add(Bytecode.IFNE);
        byte[] search = bytecode.get();

        bytecode = new Bytecode(methodInfo.getConstPool());
        bytecode.addAload(localNames.get("performer"));
        bytecode.addAload(localNames.get("source"));
        bytecode.addInvokestatic(classPool.get(this.getClass().getName()), "willMineSlope",
                Descriptor.ofMethod(CtPrimitiveType.booleanType, new CtClass[] { ctCreature, ctItem }));
        bytecode.addGap(2);
        bytecode.add(Bytecode.IFEQ);
        byte[] replacement = bytecode.get();

        new CodeReplacer(codeAttribute).replaceCode(search, replacement);
        methodInfo.rebuildStackMap(classPool);
    }
}