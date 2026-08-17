package me.alpestrine.c.reward.screen.screens.reward;

import me.alpestrine.c.reward.config.PlayerDataHandler;
import me.alpestrine.c.reward.config.objects.JsonBaseReward;
import me.alpestrine.c.reward.config.objects.JsonPlayerData;
import me.alpestrine.c.reward.config.objects.JsonStack;
import me.alpestrine.c.reward.screen.button.ItemBuilder;
import me.alpestrine.c.reward.screen.screens.AbstractACScreen;
import me.alpestrine.c.reward.screen.screens.SelectionScreen;
import me.alpestrine.c.reward.server.MainServer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractRewardScreen extends AbstractACScreen {
    public static final Item canClaim = Items.LIME_STAINED_GLASS_PANE;
    public static final Item alreadyClaimed = Items.GREEN_STAINED_GLASS_PANE;
    public static final Item cannotBeClaimed = Items.RED_STAINED_GLASS_PANE;

    public static final Item backItem = Items.RED_CONCRETE;
    public static final Item prevItem = Items.BLUE_CONCRETE;
    public static final Item nextItem = Items.PURPLE_CONCRETE;
    public static final Item invalidItem = Items.GRAY_CONCRETE;

    protected static final int maxSlot = 16;
    protected static final int minSlot = 10;

    public static final PlayerDataHandler dataHandler = new PlayerDataHandler();

    protected int page;

    protected AbstractRewardScreen() {
        this(1);
    }

    protected AbstractRewardScreen(int page) {
        setPage(page);
    }

    protected void setPage(int page) {
        this.page = Math.max(Math.min(page, getMaxPage()), 1);
    }

    protected boolean canSetPageTo(int page) {
        return page == Math.max(Math.min(page, getMaxPage()), 1);
    }

    protected int getMaxPage() {
        return (int) Math.ceil((double) elementAmount() / 7.0);
    }

    protected abstract Map<? extends Number, ? extends JsonBaseReward<?>> getRewards();

    protected int elementAmount() {
        return getRewards().size();
    }

    @Override
    public int getPage() {
        return page;
    }

    @Override
    protected void fillEmpty() {
        super.fillEmpty();

        setButton(18, ItemBuilder.start(backItem).name("Main Page")
                .button(event -> event.player.openHandledScreen(new SelectionScreen())));

        int prev = getPage() - 1;
        boolean canPrev = canSetPageTo(prev);
        setButton(25, ItemBuilder.start(canPrev ? prevItem : invalidItem).name(canPrev ? "Previous Page: " + prev : "")
                .button(canPrev ? event -> setPageForPlayer(prev, event.player) : null));

        int next = getPage() + 1;
        boolean canNext = canSetPageTo(next);
        setButton(26, ItemBuilder.start(canNext ? nextItem : invalidItem).name(canNext ? "Next Page: " + next : "")
                .button(canNext ? event -> setPageForPlayer(next, event.player) : null));
    }

    private void setPageForPlayer(int page, ServerPlayerEntity viewer) {
        setPage(page);
        init(viewer);
        viewer.currentScreenHandler.updateToClient();
    }

    @Override
    public void refresh(ServerPlayerEntity viewer) {
        setPageForPlayer(getPage(), viewer);
    }

    protected String[] getToolTip(List<JsonStack> stacks) {
        ArrayList<String> tooltips = new ArrayList<>();
        for (JsonStack stack : stacks) {
            tooltips.add("");
            tooltips.addAll(stack.toToolTip());
        }
        return tooltips.toArray(new String[0]);
    }

    protected Item getClaimItem(boolean isClaimed, boolean isClaimable) {
        return isClaimed ? alreadyClaimed : (isClaimable ? canClaim : cannotBeClaimed);
    }

    protected void onClick(boolean isClaimed, boolean isClaimable, ServerPlayerEntity player, JsonPlayerData data,
            JsonBaseReward<?> jbr, Type type) {
        if (!isClaimed && isClaimable) {
            List<ItemStack> stacks = jbr.getRewardItems().stream().map(JsonStack::toItemStack).toList();
            int empty = 0;
            for (ItemStack iStack : player.getInventory().main) {
                if (!iStack.isEmpty())
                    continue;
                empty++;
            }
            if (empty < stacks.size()) {
                player.sendMessage(Text.empty()
                        .append(String.format(
                                "Your inventory is too full to receive %s items! You only have %s empty slots.",
                                stacks.size(), empty))
                        .formatted(Formatting.RED));
                return;
            }
            for (ItemStack is : stacks) {
                player.getInventory().insertStack(is);
            }
            switch (type) {
                case Playtime -> data.claimedPlaytime.add(jbr.getId());
                case Daily -> data.claimedDaily.add(jbr.getId());
            }
            MainServer.configHandlerThread.execute(AbstractRewardScreen.dataHandler::writeCurrentValue);
            refresh(player);
        }
    }

    public enum Type {
        Playtime, Daily
    }
}
