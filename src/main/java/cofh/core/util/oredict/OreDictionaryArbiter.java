package cofh.core.util.oredict;

import cofh.lib.util.ItemWrapper;
import cofh.lib.util.helpers.ItemHelper;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import gnu.trove.map.TMap;
import gnu.trove.map.hash.THashMap;
import lombok.val;

import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * This class exists to optimize OreDictionary functionality, as it is embarrassingly slow otherwise.
 *
 * The vast majority of this functionality is safely exposed through {@link ItemHelper} via a proxy. If you use the fancy functions here, READ how they work.
 * They are typically unsafe if you are stupid. Don't be stupid.
 *
 * @author King Lemming
 *
 */
public class OreDictionaryArbiter {

	private static BiMap<String, Integer> oreIDs = HashBiMap.create();
	private static TMap<Integer, ArrayList<ItemStack>> oreStacks = new THashMap<Integer, ArrayList<ItemStack>>();

	private static TMap<ItemWrapper, ArrayList<Integer>> stackIDs = new THashMap<ItemWrapper, ArrayList<Integer>>();
	private static TMap<ItemWrapper, ArrayList<String>> stackNames = new THashMap<ItemWrapper, ArrayList<String>>();

	private static String[] oreNames = new String[] {};

	public static final String UNKNOWN = "Unknown";
	public static final int UNKNOWN_ID = -1;
	public static final int WILDCARD_VALUE = Short.MAX_VALUE;

	/**
	 * Initializes all of the entries. Called on server start to make sure everything is in sync.
	 */
	public static synchronized void initialize() {

		oreIDs = HashBiMap.create(oreIDs == null ? 32 : oreIDs.size());
		oreStacks = new THashMap<Integer, ArrayList<ItemStack>>(oreStacks == null ? 32 : oreStacks.size());

		stackIDs = new THashMap<ItemWrapper, ArrayList<Integer>>(stackIDs == null ? 32 : stackIDs.size());
		stackNames = new THashMap<ItemWrapper, ArrayList<String>>(stackNames == null ? 32 : stackNames.size());

		oreNames = OreDictionary.getOreNames();

		for (int i = 0; i < oreNames.length; i++) {
			ArrayList<ItemStack> ores = OreDictionary.getOres(oreNames[i]);

			for (int j = 0; j < ores.size(); j++) {
				registerOreDictionaryEntry(ores.get(j), oreNames[i]);
			}
		}
		val stackKeys = new HashSet<ItemWrapper>();
		do {
			val currentKeys = new HashSet<>(stackIDs.keySet());
			currentKeys.removeAll(stackKeys);
			if (currentKeys.isEmpty())
				break;
			for (ItemWrapper wrapper : currentKeys) {
				if (wrapper.metadata != WILDCARD_VALUE) {
					ItemWrapper wildItem = new ItemWrapper(wrapper.item, WILDCARD_VALUE);
					if (stackIDs.containsKey(wildItem)) {
						stackIDs.get(wrapper).addAll(stackIDs.get(wildItem));
						stackNames.get(wrapper).addAll(stackNames.get(wildItem));
					}
				}
			}
			stackKeys.addAll(currentKeys);
		} while(true);
		ItemHelper.oreProxy = new OreDictionaryArbiterProxy();
	}

	/**
	 * Register an Ore Dictionary Entry.
	 */
	public static void registerOreDictionaryEntry(ItemStack stack, String name) {

		if (stack.getItem() == null || Strings.isNullOrEmpty(name)) {
			return;
		}
		int id = OreDictionary.getOreID(name);

		oreIDs.put(name, id);

		if (!oreStacks.containsKey(id)) {
			oreStacks.put(id, new ArrayList<ItemStack>());
		}
		oreStacks.get(id).add(stack);

		ItemWrapper item = ItemWrapper.fromItemStack(stack);

		if (!stackIDs.containsKey(item)) {
			stackIDs.put(item, new ArrayList<Integer>());
			stackNames.put(item, new ArrayList<String>());
		}
		stackIDs.get(item).add(id);
		stackNames.get(item).add(name);
	}

	/**
	 * Retrieves the oreID, given an oreName.
	 *
	 * Returns -1 if there is no corresponding oreID.
	 */
	public static int getOreID(String name) {

		Integer id = oreIDs.get(name);

		return id == null ? UNKNOWN_ID : id;
	}

	/**
	 * Retrieves the oreID, given an ItemStack.
	 *
	 * If an ItemStack has more than one oreID, this returns the first one - just like Forge's Ore Dictionary.
	 *
	 * Returns -1 if there is no corresponding oreID.
	 */
	public static int getOreID(ItemStack stack) {

		if (stack == null) {
			return UNKNOWN_ID;
		}
		ArrayList<Integer> ids = stackIDs.get(new ItemWrapper(stack));

		if (ids == null) {
			ids = stackIDs.get(new ItemWrapper(stack.getItem(), WILDCARD_VALUE));
		}
		return ids == null ? UNKNOWN_ID : ids.get(0);
	}

	/**
	 * Returns a list containing ALL oreIDs for a given ItemStack. Returns NULL if there are none.
	 *
	 * Input is not validated - don't be dumb!
	 */
	public static ArrayList<Integer> getAllOreIDs(ItemStack stack) {

		ArrayList<Integer> ids = stackIDs.get(new ItemWrapper(stack));

		return ids == null ? stackIDs.get(new ItemWrapper(stack.getItem(), WILDCARD_VALUE)) : ids;
	}

	/**
	 * Retrieves the oreName, given an oreID.
	 *
	 * Returns "Unknown" if there is no corresponding oreName.
	 */
	public static String getOreName(int id) {

		String oreName = oreIDs.inverse().get(id);

		return oreName == null ? UNKNOWN : oreName;
	}

	/**
	 * Retrieves the oreName, given an ItemStack.
	 *
	 * If an ItemStack has more than one oreName, this returns the first one = just like Forge's Ore Dictionary.
	 *
	 * Returns "Unknown" if there is no corresponding oreName.
	 */
	public static String getOreName(ItemStack stack) {

		int id = getOreID(stack);

		return id == UNKNOWN_ID ? UNKNOWN : getOreName(id);
	}

	/**
	 * Returns a list containing ALL oreNames for a given ItemStack. Returns NULL if there are none.
	 *
	 * Input is not validated - don't be dumb!
	 */
	public static ArrayList<String> getAllOreNames(ItemStack stack) {

		ArrayList<String> names = stackNames.get(new ItemWrapper(stack));

		return names == null ? stackNames.get(new ItemWrapper(stack.getItem(), WILDCARD_VALUE)) : names;
	}

	/**
	 * Do not under ANY circumstances EVER modify these stacks. This is a direct return for time saving reasons.
	 *
	 * Forge's Ore Dictionary has an O(N) copy here because it assumes all modders are stupid. But you're not stupid, right?
	 *
	 * DO NOT MODIFY THESE.
	 */
	public static ArrayList<ItemStack> getOres(ItemStack stack) {

		return oreStacks.get(getOreID(stack));
	}

	/**
	 * Do not under ANY circumstances EVER modify these stacks. This is a direct return for time saving reasons.
	 *
	 * Forge's Ore Dictionary has an O(N) copy here because it assumes all modders are stupid. But you're not stupid, right?
	 *
	 * DO NOT MODIFY THESE.
	 */
	public static ArrayList<ItemStack> getOres(String name) {

		return oreStacks.get(getOreID(name));
	}

	/**
	 * Do not under ANY circumstances EVER modify this array. This is a direct return for time saving reasons.
	 *
	 * Forge's Ore Dictionary has an O(N) copy here because it assumes all modders are stupid. But you're not stupid, right?
	 *
	 * DO NOT MODIFY THIS.
	 */
	public static String[] getOreNames() {

		return oreNames;
	}

}
