package set.ex;

import java.util.concurrent.ConcurrentLinkedQueue;

import set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    protected int score;

    private Dealer dealer;

    /**
     * Player's present state
     */
    public enum gameState {
        WAITING,
        PLAYING,
        PENALTY,
        POINT
    }

    protected volatile gameState state;

    /**
     * Player's current building set
     */
    protected ConcurrentLinkedQueue<Integer> set;

    /**
     * Queue of key presses
     */
    private ConcurrentLinkedQueue<Integer> keyPresses;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        this.dealer = dealer;
        state = gameState.WAITING;
        set = new ConcurrentLinkedQueue<Integer>();
        keyPresses = new ConcurrentLinkedQueue<Integer>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        else dealer.dealerThread.interrupt(); // Notify dealer that player thread has started

        while (!terminate) {
            gameLoop(human);
        }

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            dealer.dealerThread.interrupt(); // Notify dealer that player thread has started

            while (!terminate) {
                gameLoop(human);
            }
            
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
        try { aiThread.join(); } catch (InterruptedException ignored) {}
    }

    /**
     * Player's game loop
     */
    private void gameLoop(boolean human) {
        while (state == gameState.WAITING & !terminate) 
            try {
                synchronized(this) {this.wait();}
            } catch (InterruptedException e) {}

        if (human & state == gameState.PLAYING) {
            // Wait for input
            try {
                synchronized(this) {this.wait();}
            } catch (InterruptedException e) {
                performPresses();
            }
        } else if (!human & state == gameState.PLAYING) {
            randomPress();
            performPresses();
        }
        if (terminate) return;

        // Double check gamestate in case player missed an interrupt
        if (state == gameState.POINT) {point();}
        else if (state == gameState.PENALTY) {penalty();}
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // Ignore if no card is present in slot, if player tries to place their 4th token, or if player isn't currently playing
        if (table.slotToCard[slot] != null &&
            !(table.tokens[slot][this.id].get() == false & set.size() == 3) &&
            state == gameState.PLAYING) {
                keyPresses.add(slot);
                if (human) {playerThread.interrupt();} // Human thread waiting for input from keyboard
        }
    }

    private void performPresses() {
        while (!keyPresses.isEmpty()) {
            int slot = keyPresses.poll();
            // Synchronization here to make sure table.slotToCard[slot] wouldn't turn null due to other players.
            synchronized(table) {
                if (table.slotToCard[slot] != null) {
                    if (table.tokens[slot][this.id].get() == false) { // Player places token
                        set.add(slot);
                        table.placeToken(this.id, slot);
                    } else { // Player removes token
                        set.remove(slot);
                        table.removeToken(this.id, slot);
                    }
                }
            }
            if (set.size() == 3) {
                checkMySet();
            }
        }
    }

    /**
     * Pass dealer the player's set for checking, react according to result.
     */
    private void checkMySet() {
        state = gameState.WAITING;
        dealer.checkSet.add(this);
        dealer.dealerThread.interrupt();
        try {
            synchronized(this) {this.wait();}
        } catch (InterruptedException setBeenChecked) {} // Wait for dealer to check set
        if (state == gameState.POINT) {point();}
        else if (state == gameState.PENALTY) {penalty();}
    }

    private void point() {
        env.ui.setScore(id, ++score);
        freeze(env.config.pointFreezeMillis);
        if (!table.reset) state = gameState.PLAYING;
        else state = gameState.WAITING;   
    }

    private void penalty() {
        removeTokens();
        freeze(env.config.penaltyFreezeMillis);
        if (!table.reset) state = gameState.PLAYING;
        else state = gameState.WAITING;
    }

    /**
     * Remove all player's tokens from table.
     */
    protected void removeTokens() {
        while (!set.isEmpty()) {
            int slot = set.poll();
            table.removeToken(id, slot);
        }
    }

    private void freeze(long freezeTime) {
        long finishTime = System.currentTimeMillis() + freezeTime;
        while (finishTime > System.currentTimeMillis() & !terminate) {
            env.ui.setFreeze(id, finishTime - System.currentTimeMillis() + 900); // + 900 for playability: displays integer part of freezeTime
            try {
                if (finishTime - System.currentTimeMillis() > 1000) {
                    Thread.sleep(1000);
                }
                else {
                    Thread.sleep(Long.max(finishTime - System.currentTimeMillis(), 0));
                }
            } catch (Exception ignored) {}
        }
        if (terminate) return;
        env.ui.setFreeze(id, 0);
    }

    private void randomPress() {
        keyPressed((int)(Math.random() * env.config.tableSize));
    }

    protected Thread getThread() {
        if (human) return playerThread;
        else return aiThread;
    }
}