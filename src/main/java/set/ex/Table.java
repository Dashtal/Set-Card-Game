package set.ex;

import set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Mapping between cards to players' tokens.
     */
    protected final AtomicBoolean[][] tokens;

    /**
     * Indicates of table currently being resetted.
     */
    protected volatile boolean reset;

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env _env) {
        env = _env;
        slotToCard = new Integer[env.config.tableSize];
        cardToSlot = new Integer[env.config.deckSize];

        tokens = new AtomicBoolean[env.config.tableSize][env.config.players];
        for (int size = 0; size < env.config.tableSize; size++) {
            for (int player = 0; player < env.config.players; player++) {
                tokens[size][player] = new AtomicBoolean(false);
            }
        }
        reset = true;
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

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        slotToCard[slot] = card;
        cardToSlot[card] = slot;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    // RemoveCard is synchronized for keeping changes in player's tokens safe.
    public synchronized void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        // Remove all players' tokens from slot
        for(int i = 0; i < env.config.players; i++) {
            if (tokens[slot][i].get() == true) {
                removeToken(i, slot);
            }
        }
        int card = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[card] = null;
        
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        tokens[slot][player].set(true);
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public void removeToken(int player, int slot) {
        tokens[slot][player].set(false);
        env.ui.removeToken(player, slot);
    }
}
