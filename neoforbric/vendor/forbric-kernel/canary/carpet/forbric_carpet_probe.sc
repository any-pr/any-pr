__config() -> {'scope' -> 'global'};
global_swaps = 0;
global_breaks = 0;
global_previous = '';
global_cancel = true;
global_clear_main = false;
__on_player_swaps_hands(player) -> (
    global_swaps += 1;
    if(global_clear_main, inventory_set(player, player~'selected_slot', 0));
    if(global_cancel, 'cancel', null)
);
__on_player_breaks_block(player, previous_block) -> (
    global_breaks += 1;
    global_previous = str(previous_block);
    if(global_cancel, 'cancel', null)
);
