# Alpha dangerous-tool default-off proposal

This is a proposal for the next permission-default revision; this Alpha pass does not silently rewrite an owner's existing permission settings.

The following capabilities should be disabled on a fresh install until the owner explicitly enables them for a task:

- Arbitrary dispatch and external navigation: `send_intent`, `open_uri`.
- Notification mutation or sending: `notification_open`, `notification_dismiss`, `notification_snooze`, `notification_action`, `notification_reply`.
- File/network mutation: `write_file`, `append_file`, `file_replace`, `download_from_url`, `delete_file`, `share_file_via_web`.
- Privacy-sensitive sensors/data: `get_clipboard`, `set_clipboard`, `get_device_logs`, `get_location`, all photo/video capture and save tools (and especially recorded audio).
- State-changing app actions that are not needed for basic screen control: `close_app`, alarms and timers.

Core screen reading, node lookup, screenshot fallback, tap/swipe, app opening and text input remain necessary for the advertised control path. During Alpha they rely on the on-device pause switch, an exclusive AI session and owner confirmation at sensitive boundaries. A later release should implement audited presets instead of a single universal default.
