package set.ex;

import set.Env;

public class Player_Human extends Player {

    public Player_Human(Env env, Dealer dealer, Table table, int id) {
        super(env, dealer, table, id);
    }

    @Override
    public void run() {
        playerThread = Thread.currentThread();
        notifyDealer();

        while (!terminate) {
            // Wait for input
            try {
                synchronized(this) {wait();}
            } catch (InterruptedException keyPressed) {
                // Human player gets interruptions from both InputManager and Dealer, need to distinguish between the two.
                if (keyInput != null)
                    executePress();
            }
        }
    }

    /**
     * This method is called by InputManager when a key is pressed.
     */
    @Override
    public void keyPressed(int slot) {
    // Ignore if player waits for set to be checked.
        if (state == gameState.PLAYING && setSize < 3) {
            keyInput = slot;
            // Human thread waiting for input from keyboard
            playerThread.interrupt();
        }
    }
}
