package minesweeper

import kotlin.random.Random

class Game {
    companion object {
        // trying to support boards of any size
        const val BOARD_ROWS = 1000
        const val BOARD_COLS = 1000
        const val BOARD_SIZE = BOARD_ROWS * BOARD_COLS

        // needed to calculate the prefix for rows (when BOARD_ROWS > 9)
        const val BOARD_COL_DIGITS = BOARD_COLS.toString().length

        // values for the field states
        // values 1 to 8 are used to represent the number of mines this field (= info)
        const val FIELD_EMPTY = 0
        const val FIELD_MINE = -1
        const val FIELD_MARK_MINE = -2
        const val FIELD_MARK_EMPTY = 9
        const val FIELD_EMPTY_EXPLORED = 10

        // modifiers for the field states -- only used for info now
        const val MOD_VISIBILITY = 20
        const val MOD_MARKED = 10

        // these are the relative x to y-coordinates of the cells around a cell
        val cellsAround = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
    }

    // game state
    private var gameOver = false

    // make an empty board
    private val board = List(BOARD_ROWS) { MutableList(BOARD_COLS) { FIELD_EMPTY } }

    // check if coordinates are within bounds
    private fun validCoordinates(x: Int, y: Int) = x >= 0 && y >= 0 && x < BOARD_COLS && y < BOARD_ROWS

    // update the hint at the given coordinates
    private fun updateHint(x: Int, y: Int) {
        if (validCoordinates(x, y) && board[y][x] >= FIELD_EMPTY) {
            board[y][x]++
        }
    }

    // put a mine at the given position (n-th cell on the board)
    private fun putMineAtPosition(pos: Int): Boolean {
        // calculate the coordinates from the given position
        val x = pos % BOARD_COLS
        val y = pos / BOARD_COLS

        // if there already is a mine at this spot, report failure
        if (board[y][x] < 0) return false

        // else place a mine ...
        board[y][x] = FIELD_MINE

        // ... and update the hints around it
        cellsAround.forEach { updateHint(x + it.first, y + it.second) }

        // report success
        return true
    }

    fun setupGame(number: Int) {
        // make sure we don't create an infinite loop
        var minesToPlace = minOf(number, BOARD_SIZE)

        // generate all the mines randomly until done
        while (minesToPlace > 0) {
            if (putMineAtPosition(Random.nextInt(BOARD_SIZE))) {
                minesToPlace--
            }
        }
    }

    fun printBoard() {
        board.forEachIndexed { rowIndex, row ->
            // print header if this is the first row
            if (rowIndex == 0) {
                // if we have more than 9 columns we need other formatting
                if (BOARD_COLS > 9) {
                    print(" ".repeat(BOARD_COL_DIGITS) + "│")

                    // print extra header row with labels for tens and hundreds
                    var skipDigits = 0
                    repeat(BOARD_COLS) { x ->
                        // check if a new tens-label is needed at this spot
                        if ((x + 1) % 10 == 0) {
                            print((x + 1) / 10)
                            skipDigits = ((x + 1) / 10).toString().length - 1
                        } else {
                            if (skipDigits-- <= 0) print(" ")
                        }
                    }
                    println("│")
                }

                // this is the default header row with the number labels (123456789)
                print(" ".repeat(BOARD_COL_DIGITS) + "│")
                repeat(BOARD_COLS) { print((it + 1) % 10) }
                println("│")

                // divider row
                println("—".repeat(BOARD_COL_DIGITS) + "│" + "—".repeat(BOARD_COLS) + "|")
            }

            // loop through rows and for each row, print all fields
            print(" ".repeat(BOARD_COL_DIGITS - (rowIndex + 1).toString().length) + "${rowIndex + 1}|")
            row.forEach { field ->
                print(
                    when (field) {
                        FIELD_EMPTY_EXPLORED -> '/'
                        FIELD_MARK_MINE, FIELD_MARK_EMPTY,
                        in MOD_MARKED + 1..MOD_MARKED + 8 -> '*'
                        in MOD_VISIBILITY + 1..MOD_VISIBILITY + 8 -> field % 10
                        else -> "."
                    }
                )
            }
            println("|")

            // print footer if this was the last row
            if (rowIndex == BOARD_ROWS - 1) {
                println("—".repeat(BOARD_COL_DIGITS) + "│" + "—".repeat(BOARD_COLS) + "|")
            }
        }
    }

    fun markMine(x: Int, y: Int) {
        if (!validCoordinates(x, y)) return

        when (board[y][x]) {
            FIELD_MINE -> board[y][x] = FIELD_MARK_MINE
            FIELD_EMPTY -> board[y][x] = FIELD_MARK_EMPTY
            FIELD_MARK_MINE -> board[y][x] = FIELD_MINE
            FIELD_MARK_EMPTY -> board[y][x] = FIELD_EMPTY
            in 1..8 -> board[y][x] += MOD_MARKED
            in MOD_MARKED + 1..MOD_MARKED + 8 -> board[y][x] -= MOD_MARKED
        }

        printBoard()
    }

    // applies operations on inner fields, let caller know if we are inside the filling area
    private fun checkInside(x: Int, y: Int): Boolean {
        if (board[y][x] == FIELD_EMPTY || board[y][x] == FIELD_MARK_EMPTY) {
            board[y][x] = FIELD_EMPTY_EXPLORED
            return true
        }
        return false
    }

    // applies operations on border-fields
    private fun markOutside(x: Int, y: Int) {
        // if this field has info, mark it
        if (board[y][x] in 1..8) board[y][x] += MOD_VISIBILITY

        // if this field has marked info, unmark it and make it visible
        else if (board[y][x] in MOD_MARKED + 1..MOD_MARKED + 8) board[y][x] += MOD_VISIBILITY - MOD_MARKED
    }

    // using Flood-fill algorithm prevents StackOverFlowErrors because we're using our own stack
    // inside = the function which checks if we are inside the filling area and performs the operation on this cell
    // outside = the function we want to invoke on the border cells
    private fun floodFill(pos_x: Int, pos_y: Int, inside: (Int, Int) -> Boolean, outside: (Int, Int) -> Unit) {
        // using Set will force unique pairs, should perform better (not sure how much difference)
        val stack = mutableSetOf<Pair<Int, Int>>()

        // default implementation of Set, LinkedHashSet, maintains order of insertion
        // so .add will add to the end of the queue
        stack.add(pos_x to pos_y)

        while (stack.size > 0) {
            val (x, y) = stack.first()
            stack.remove(x to y)

            if (validCoordinates(x, y)) {
                if (inside(x, y)) {
                    for ((xMod, yMod) in cellsAround) {
                        stack.add(x + xMod to y + yMod)
                    }
                } else {
                    outside(x, y)
                }
            }
        }
    }

    fun testFree(x: Int, y: Int) {
        if (!validCoordinates(x, y)) return

        // if we stepped on a mine, the game is over
        if (board[y][x] < 0) {
            println("You stepped on a mine and failed!")
            printBoard()
            gameOver = true
            return
        }

        // start marking cells as empty or info
        floodFill(x, y, ::checkInside, ::markOutside)
        printBoard()
    }

    fun hasEnded(): Boolean {
        if (gameOver) return true

        // user wins if there are no unmarked mines or marked empty fields left
        if (board.flatten().count { it == FIELD_MINE || it == FIELD_MARK_EMPTY } == 0) {
            println("Congratulations! You found all the mines!")
            return true
        }

        return false
    }
}

fun main() {
    val game = Game()

    // set up the board and show it
    print("How many mines do you want on the field?")
    game.setupGame(readln().toInt())
    game.printBoard()

    // main game loop
    do {
        print("Set/unset mine marks or claim a cell as free:")
        val input = readln().split(" ")
        val x = input[0].toInt() - 1
        val y = input[1].toInt() - 1

        if (input.size > 2 && input[2] == "mine") {
            game.markMine(x, y)
        } else {
            game.testFree(x, y)
        }
    } while (!game.hasEnded())
}