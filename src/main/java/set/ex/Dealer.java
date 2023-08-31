package set.ex;

import set.Env;
import set.ex.Player.gameState;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private final Timer timer;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    // Volatile - Main thread and Dealer thread.
    private volatile boolean terminate;

    /**
     * Notifications being passed between entities through interrupting threads.
     */
    protected Thread dealerThread;

    /**
     * Indicates that round currently ongoing.
     */
    // Volatile - Used by timer, dealer, players.
    protected volatile boolean roundFinished;

    /**
     * Queue of ids of players waiting for the sets being checked by dealer.
     */
    // Used by players and dealer threads.
    protected ConcurrentLinkedQueue<Player> playersSets;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        timer = new Timer(this, env);
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersSets = new ConcurrentLinkedQueue<Player>();
    }

    /**
     * Initialization of the dealer thread.
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        // Initialize players threads and timer thread.
        for (int i = 0; i < players.length; i++) {
            new Thread(players[i]).start();
        }
        new Thread(timer).start();

        letsPlay();
        
        // If game finished properly and not due to an external event.
        if (!terminate) {
            terminate();
            announceWinners();
        } 
    }

    /**
     * Main loop of the game, handled by the dealer.
     */
    private void letsPlay() {
        while (!shouldFinish()) {
            // Prepare new round.
            Collections.shuffle(deck);
            placeCardsOnTable();
            roundFinished = false;
            startTimer();
            notifyAllPlayers(gameState.PLAYING);

            dealerLoop();

            // Round finished.
            notifyAllPlayers(gameState.WAITING);
            playersSets.clear();
            if (!shouldFinish()) {removeAllCardsFromTable();}
        }
    }

    /**
     * Main loop of the dealer during the game.
     * Dealer being awaken by:
     * 1. Timer signaling end of round.
     * 2. Player waiting for their set to be checked.
     * 3. User closed the game.
     */
    private void dealerLoop() {
        while (!(roundFinished || terminate)) {
            checkSets();
            try {
                synchronized (this) {wait();}
            } catch (InterruptedException dealerAwaken) {}
        }
    }

    /**
     * Iterate through all sets waiting to be checked.
     */
    private void checkSets() {
        while(!playersSets.isEmpty() && !terminate) {
            Player player = playersSets.poll();
            int[][] slotsAndCards = constructSet(player);
            int[] slots = slotsAndCards[0];
            int[] cards = slotsAndCards[1];
            boolean valid = env.util.testSet(cards);

            // Wrap if-else statement in a lock because shared data is being manipulated in both cases.
            table.rwLock.writeLock().lock();
            if (valid) {
                player.state = gameState.POINT;
                handleLegalSet(slots);
            } else {
                player.state = gameState.PENALTY;
                for (int slot : slots) {table.removeToken(player.id, slot);} // Remove player's tokens.
            }
            table.rwLock.writeLock().unlock();
            player.playerThread.interrupt();
        }
    }

    /**
     * Remove cards and tokens from corresponding slots, awake players which their tokens have been removed.
     */
    private void handleLegalSet(int[] set) {
        for (int slot : set) {
            for (Player player : players) {
                if (table.tokens[player.id][slot] == true) {
                    table.removeToken(player.id, slot);
                    playersSets.remove(player);
                    if (player.state == gameState.WAITING) {
                        player.state = gameState.PLAYING;
                        players[player.id].playerThread.interrupt();
                    }
                }
            }
            table.removeCard(slot);
        }
        placeCardsOnTable();
    }

    /**
     * Returns player's set to be checked by dealer.
     */
    private int[][] constructSet(Player player) {
            int[][] slotsAndCards = new int[2][3];
            int count = 0;
            // Dealer reads here from shared data (table). No need to lock because Dealer is the only writer.
            for (int slot = 0; slot < env.config.tableSize && count < 3; slot++) {
                if (table.tokens[player.id][slot] == true) {
                    slotsAndCards[0][count] = slot;
                    slotsAndCards[1][count] = table.slotToCard[slot];
                    count++;
                }
            }
            return slotsAndCards;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> randomSlot = shuffleSlots();
        for (int i = 0; i < env.config.tableSize && !terminate && !deck.isEmpty(); i++) {
            Integer slot = randomSlot.remove(0);
            if (table.slotToCard[slot] == null) {
                table.placeCard(deck.remove(0), slot);
            }
        }
        if (env.config.hints == true && !terminate) table.hints();
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        List<Integer> randomSlots = shuffleSlots();
        for (Integer slot : randomSlots) {
            Integer card = table.slotToCard[slot];
            if (card != null) {
                // Remove all tokens from card.
                for (Player player : players) {
                    if (table.tokens[player.id][slot] == true)
                        table.removeToken(player.id, slot);
                }
                table.removeCard(slot);
                deck.add(card);

                if (terminate) break;
            } 
        }
    }

    /**
     * Find winners and display them.
     */
    private void announceWinners() {
        int noOfWinners = 0;
        int maxScore = 0;
        for (Player player : players) {
            int playerScore = player.score;
            if (maxScore == playerScore)
                noOfWinners++;
            if (maxScore < playerScore) {
                maxScore = playerScore;
                noOfWinners = 1;
            }
        }
        int[] winners = new int[noOfWinners];
        for (Player player : players) {
            if (player.score == maxScore) {
                winners[noOfWinners - 1] = player.id;
                noOfWinners--;
            }
        }
        env.ui.announceWinner(winners);
    }

    /**
     * Stop / Resume players in game.
     */
    private void notifyAllPlayers(gameState state) {
        for (Player player : players) {
            player.state = state;
            player.keyInput = null;
            player.playerThread.interrupt();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Called when game exits due to external event.
     * Terminates all threads.
     */
    public void terminate() {
        // Terminate players.
        for (Player player : players) {
            player.terminate = true;
            player.playerThread.interrupt();
        }
        // Terminate timer.
        timer.terminate = true;
        timer.timerThread.interrupt();
        // Terminate dealer.
        terminate = true;
        dealerThread.interrupt();
    }

    private List<Integer> shuffleSlots() {
        List<Integer> output = new LinkedList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++) {
            output.add(i);
        }
        Collections.shuffle(output);
        return output;
    }

    private void startTimer() {
        timer.timerThread.interrupt();
    }
}