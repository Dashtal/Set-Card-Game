# Set-Card-Game
This project was part of the Systems Programming course at Ben Gurion University.
I was passionate about the assignment, and when I got the chance a few months later I went ahead and re-implemented it, making it even faster, more effective and modular.
The assignment was making a safe and correct implementation of the card game "Set", while keeping an active thread for the dealer and each player.
The goal was to practice concurrent programming, and I believe I took it to the extreme in this implementation.
1. For this implementation I've been using a Read-Write lock between the dealer (the writer) and players (the readers).
2. Whenever a thread is not fulfilling an assignment it waits until being awaken - optimization of CPU time.
3. OOP principles brought to light - Human and Bot players inherited from an abstract Player class. Table class which holds all shared data. Dealer, Timer.
