package one.lindegaard.MobHunting.rewards;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import one.lindegaard.Core.Core;
import one.lindegaard.Core.rewards.Reward;
import one.lindegaard.MobHunting.MobHunting;

public class MoneyMergeEventListener_Delete implements Listener {

	private MobHunting plugin;

	public MoneyMergeEventListener_Delete(MobHunting plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void onMoneyMergeEvent(ItemMergeEvent event) {
		// OBS: ItemMergeEvent does only exist in MC1.8 and newer

		if (event.isCancelled())
			return;

		Item item1 = event.getEntity();
		Item item2 = event.getTarget();
		ItemStack is2 = item2.getItemStack();
		if (Reward.isReward(item1) && Reward.isReward(item2)) {
			Reward reward1 = Reward.getReward(item1);
			Reward reward2 = Reward.getReward(item2);
			if (reward1.getRewardType().equals(reward2.getRewardType())) {
				if (reward1.isBagOfGoldReward() || reward1.isItemReward()) {
					if (reward1.getMoney() + reward2.getMoney() != 0) {
						reward2.setMoney(reward1.getMoney() + reward2.getMoney());
						is2=Reward.setDisplayNameAndHiddenLores(is2.clone(), reward2);
						item2.setItemStack(is2);
						item2.setMetadata(Reward.MH_REWARD_DATA_NEW,
								new FixedMetadataValue(MobHunting.getInstance(), new Reward(reward2)));
						plugin.getMessages().debug("Money merged - new value=%s",
								plugin.getRewardManager().format(reward2.getMoney()));
					}
				} else if (reward1.isKilledHeadReward() || reward1.isKillerHeadReward()) {
					if (reward1.getMoney() == reward2.getMoney()) {
						is2=Reward.setDisplayNameAndHiddenLores(is2.clone(), reward2);
						item2.setItemStack(is2);
						item2.setMetadata(Reward.MH_REWARD_DATA_NEW,
								new FixedMetadataValue(MobHunting.getInstance(), new Reward(reward2)));
						plugin.getMessages().debug("Heads merged - value=%s - each head",
								plugin.getRewardManager().format(reward2.getMoney()));
					}
				}
				if (Core.getCoreRewardManager().getDroppedMoney().containsKey(item1.getEntityId()))
					Core.getCoreRewardManager().getDroppedMoney().remove(item1.getEntityId());
			}
		}
	}
}
