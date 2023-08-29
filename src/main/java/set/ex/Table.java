package set.ex;
import set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class contains the data that is visible to the player.
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Player[] players;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /*
     * A grid that holds all player tokens.
     */
    protected final boolean[][] tokens;

    /**
     * Cards on table being accessed through a Read-Write lock.
     * The reason for that is to allow all players to access the table simultaneously, except when the dealer is using the table.
     */
    protected final ReadWriteLock rwLock;

    /**
     * Table constructor.
     * @param env - the game environment objects.
     */
    public Table(Env env, Player[] players) {
        this.env = env;
        this.players = players;
        slotToCard = new Integer[env.config.tableSize];
        cardToSlot = new Integer[env.config.deckSize];
        tokens = new boolean[env.config.players][env.config.tableSize];
        rwLock = new ReentrantReadWriteLock();
    }

    /**
     * Places a card on the table in a grid slot.
     */
    public void placeCard(int card, int slot) {
        // UX/UI.
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // Place card.
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     */
    public void removeCard(int slot) {
        // UX/UI.
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        
        // Remove card.
        int card = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[card] = null;
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     */
    public void placeToken(int player, int slot) {
        tokens[player][slot] = true;
        players[player].setSize++;
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     */
    public void removeToken(int player, int slot) {
        tokens[player][slot] = false;
        players[player].setSize--;
        env.ui.removeToken(player, slot);
    }

        /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }
}