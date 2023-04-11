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

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Queue of players to check their sets.
     */
    protected ConcurrentLinkedQueue<Player> checkSet;

    /**
     * Threads of players.
     */
    private Thread[] playerThreads;

    /**
     * Dealer thread.
     */
    protected Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        checkSet = new ConcurrentLinkedQueue<Player>();
        playerThreads = new Thread[env.config.players];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        // Initialize player threads in increasing order
        for (int i = 0; i < players.length; i++) {
            playerThreads[i] = new Thread(players[i], "Player " + i);
            playerThreads[i].start();
            try {
                synchronized(this) {this.wait();}
            } catch (InterruptedException playerThreadActivated) {} // Wait for player to start
        }

        while (!shouldFinish()) {
            stopPlayers();
            checkSet.clear();

            removeAllCardsFromTable();
            Collections.shuffle(deck);
            placeCardsOnTable();
            if (terminate) break;
            
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(env.config.turnTimeoutMillis);
            resumePlayers();
            table.reset = false;

            timerLoop();
            table.reset = true;
        }
        endGame();
        // If game finished properly and not due external event
        if (!terminate) announceWinners();
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            updateTimerDisplay(timeLeft);
            if (timeLeft > env.config.turnTimeoutWarningMillis & timeLeft > 1000) {
                sleepUntilWokenOrTimeout(1000); // 1 second
            }
            else {
                sleepUntilWokenOrTimeout(1); // 1 millisecond
            }
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        this.terminate = true;
        dealerThread.interrupt();
    }

    /**
     * Tasks being handled by dealer when game ends.
     */
    private void endGame() {
        // terminate threads in decreasing order
        for (int i = playerThreads.length - 1; i >= 0; i--) {
            players[i].terminate();
            players[i].getThread().interrupt();
            try {
                players[i].getThread().join();
            } catch (InterruptedException e) {}
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> randomSlot = shuffleSlots();
        for (int i = 0; i < env.config.tableSize & !terminate; i++) {
            Integer slot = randomSlot.remove(0);
            if (!deck.isEmpty() && table.slotToCard[slot] == null) 
                table.placeCard(deck.remove(0), slot);
        }
        if (terminate) return;

        if (env.config.hints == true) table.hints();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout(long sleepTime) {
        long finishTime = System.currentTimeMillis() + sleepTime;
        while (System.currentTimeMillis() < finishTime & !terminate) {
            try {
                Thread.sleep(Long.max(0, finishTime - System.currentTimeMillis()));
            } catch (InterruptedException checkSet) {
                checkSets();
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(long timeLeft) {
        if (timeLeft > env.config.turnTimeoutWarningMillis) 
            env.ui.setCountdown(timeLeft + 900, false);
            // + 900 for playability: displays integer part of timeLeft
        else env.ui.setCountdown(timeLeft, true);
        
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (Player player : players) {
            player.removeTokens();
        }
        List<Integer> randomSlots = shuffleSlots();
        for (Integer slot : randomSlots) {
            Integer card = table.slotToCard[slot];
            if (card != null) {
                table.removeCard(slot);
                deck.add(card);

                if (terminate) break;
            } 
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
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
     * Change all players state to WAITING.
     */
    private void stopPlayers() {
        for (Player player : players) {
            player.state = gameState.WAITING;
            player.getThread().interrupt();
        }
    }

    /**
     * Change all players state to PLAYING.
     */
    private void resumePlayers() {
        for (Player player : players) {
            player.state = gameState.PLAYING;
            player.getThread().interrupt();
        }
    }

    /**
     * Iterate through all sets waiting to be checked.
     */
    private void checkSets() {
        while(!checkSet.isEmpty()) {
            Player player = checkSet.poll();
            ConcurrentLinkedQueue<Integer> set = player.set;
            int[] arrset = setAsArray(player);

            if (env.util.testSet(arrset) == true) { // Set is valid
                while (!set.isEmpty()) {
                    int slot = set.poll();
                    table.removeCard(slot);
                    // Remove card from other players' set
                    for(Player other : players) {
                        if (other.set.remove(slot) == true) {
                            checkSet.remove(other); // Remove other player from dealer's tasks
                            other.state = gameState.PLAYING;
                            players[other.id].getThread().interrupt();
                        }
                    }
                }
                placeCardsOnTable();
                player.state = gameState.POINT;

            } else { // Set is invalid
                player.state = gameState.PENALTY;
            }

            player.getThread().interrupt();
        }
    }

    private List<Integer> shuffleSlots() {
        List<Integer> output = new LinkedList<Integer>();
        for (int i = 0; i < env.config.tableSize; i++) {
            output.add(i);
        }
        Collections.shuffle(output);
        return output;
    }

    /**
     * Returns player's set as int[].
     */
    private int[] setAsArray(Player player) {
        int[] arrset = new int[3];
            int i = 0;
            for (int j = 0; j < env.config.tableSize; j++) {
                if (table.tokens[j][player.id].get() == true) {
                    arrset[i] = table.slotToCard[j];
                    i++;
                }
        }
        return arrset;
    }
}
