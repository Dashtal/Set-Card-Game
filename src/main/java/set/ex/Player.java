package set.ex;

import set.Env;

/**
 * This class manages the players' threads and data
 */
public abstract class Player implements Runnable {

    /**
     * The game environment object.
     */
    protected final Env env;

    /**
     * Game entities.
     */
    protected final Table table;
    protected final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * True iff game should be terminated.
     */
    // Volatile - Dealer thread and Player thread.
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    protected int score;

    /**
     * Player's present state
     */
    public enum gameState {
        WAITING,
        PLAYING,
        PENALTY,
        POINT
    }
    /**
     * Player's present state.
     */
    // Volatile - Dealer thread, InputManager thread and Player thread.
    protected volatile gameState state;

    /**
     * Size of current set being constructed by player.
     */
    // Volatile - InputManager thread, Dealer thread and Player thread.
    protected volatile int setSize;

    /**
     * Notifications being passed between entities through interrupting threads.
     */
    protected volatile Thread playerThread;

    /**
     * Next play.
     */
    // Volatile - InputManager thread, Dealer thread and Player thread.
    protected volatile Integer keyInput;

    /**
     * Player constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     */
    public Player(Env env, Dealer dealer, Table table, int id) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        state = gameState.WAITING;
    }

    /**
     * Main loop of the player during the game.
     */
    @Override
    public abstract void run();

    /**
     * This method is called by InputManager when a key is pressed.
     */
    public abstract void keyPressed(int slot);

    /**
     * Execute given key input.
     */
    protected void executePress() {
        table.rwLock.readLock().lock();
            // Ignore if no card present on chosen slot.
            if (table.slotToCard[keyInput] != null) {
                if (table.tokens[id][keyInput] == false) {
                    table.placeToken(id, keyInput);
                } else if (table.tokens[id][keyInput] == true) {
                    table.removeToken(id, keyInput);
                }
            }
        table.rwLock.readLock().unlock();

        if (setSize == 3) {
            checkMySet();
        }
    }

    /**
     * Player gives dealer their set to check if legal.
     */
    protected void checkMySet() {
        state = gameState.WAITING;
        dealer.playersSets.add(this);
        notifyDealer();
        // Wait for dealer to check for legal set.
        try {
            synchronized(this) {wait();}
        } catch (InterruptedException setBeenChecked) {}
        if (state == gameState.POINT) {point();}
        else if (state == gameState.PENALTY) {penalty();}
        // else cards have been used by some other player - continue.
    }

    /**
     * Award a point to a player and perform other related actions.
     */
    public void point() {
        env.ui.setScore(id, ++score);
        freeze(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        freeze(env.config.penaltyFreezeMillis);
    }

    /**
     * Cooldown after submitting set to dealer.
     */
    protected void freeze(long freezeTime) {
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

        if (!dealer.roundFinished) {
            state = gameState.PLAYING;
        } else {
            state = gameState.WAITING;
        }
    }

    protected void notifyDealer() {
        dealer.dealerThread.interrupt();
    }
}