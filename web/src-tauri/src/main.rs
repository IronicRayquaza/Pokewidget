// The desktop app is the web app in a window: all the logic lives in TypeScript, and this
// only provides the things a browser tab cannot — frameless transparent windows that float
// above the desktop, and remembering where each one was left.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    tauri::Builder::default()
        // Every widget window keeps its own size and position between runs, which is what
        // makes them feel placed rather than opened.
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .run(tauri::generate_context!())
        .expect("error while running PokeWidget");
}
