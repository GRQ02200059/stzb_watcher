# Protocol coverage: client 9.2.2

| Metric | Count |
|---|---:|
| Captured commands | 94 |
| Client-named commands | 90 |
| Registered fields | 28 |
| Shape drift commands | 15 |
| Invalid scanned samples | 153 |
| Web typed | 9 |
| Web raw | 85 |
| Web unsupported | 0 |
| Android typed | 10 |
| Android raw | 84 |
| Android unsupported | 0 |

## Commands

| Hex | Decimal | Names | Samples | Shape | Web | Android | Evidence |
|---|---:|---|---:|---|---|---|---|
| 00000002 | 2 | CREATE_ROLE | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000000a | 10 | GET_ALL_BATTLE_REPORT_PROFILE_CMD | 5 | array | typed | typed | CLIENT_CONFIRMED |
| 00000015 | 21 | GET_FIELD_INFO_CMD | 14 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000019 | 25 | SYNC_SERVER_TIME_STAMP | 7 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000001f | 31 | ARMY_REMOVE_HERO_FROM_ARMY | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000025 | 37 | ARMY_RECRUIT_BATCH | 5 | integer | raw | raw | CAPTURE_CONFIRMED |
| 0000003c | 60 | REINFORCE_FIELD_CMD | 2 | object | raw | raw | CAPTURE_CONFIRMED |
| 0000005c | 92 | GET_UNION_BATTLE_REPORT | 35 | array | typed | typed | CLIENT_CONFIRMED |
| 00000064 | 100 | UNION_OVERVIEW | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000067 | 103 | UNION_MEMBER_LIST | 2 | array | typed | typed | CLIENT_CONFIRMED |
| 00000087 | 135 | UNION_NPC_CITY_LIST | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000008e | 142 | UNION_GET_GROUP_LIST | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000008f | 143 | UNION_GET_ALL_MEMBER_LIST_FOR_CHAT | 3 | array | raw | raw | CLIENT_CONFIRMED |
| 00000099 | 153 | ARMY_CANCEL_ACTION_AND_STAY | 1 | boolean | raw | raw | CAPTURE_CONFIRMED |
| 000000ab | 171 | SWITCH_ROLE_QUERY_ROLE_LIST | 7 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000ac | 172 | SWITCH_ROLE_QUERY_HELP_ID | 6 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000bf | 191 | SEND_ACSDK_CHEAT_INFO | 206 | boolean | raw | raw | CAPTURE_CONFIRMED |
| 000000ca | 202 | MAIL_INBOX | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000cb | 203 | MAIL_OUTBOX | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000cc | 204 | MAIL_INFO | 4 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000d0 | 208 | MAIL_REWARD | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000dc | 220 | MAIL_GET_CONTACTS | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000dd | 221 | MAIL_REWARD_ONE_KEY | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 000000e2 | 226 | MAIL_REWARD_ONE_KEY_QUERY | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000105 | 261 | MINI_MAP_WORLD_INFO | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000106 | 262 | MINI_MAP_REGION_INFO | 34 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000190 | 400 | TASK_AWARD | 19 | integer | raw | raw | CAPTURE_CONFIRMED |
| 000001fd | 509 | USER_GET_SEASON_COURSE_LIST | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 000001fe | 510 | USER_GET_USER_SEASON_COURSE | 34 | object | typed | typed | CLIENT_CONFIRMED |
| 000001ff | 511 | USER_GET_RANDOM_NAME | 1 | string | raw | raw | CAPTURE_CONFIRMED |
| 0000029f | 671 | CARD_RECORD | 2 | string | typed | typed | CLIENT_CONFIRMED |
| 000002b6 | 694 | SYNC_SERVER_TIME | 2127 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002ba | 698 | MINI_MAP_NPC_CITY_INFO | 2 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002c5 | 709 | CHAT_CHANGED_NOTIFY | 17 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002c7 | 711 | CHAT_HISTORY | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002ca | 714 | GET_BLACK_LIST | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002d3 | 723 | CHAT_RECYCLE_USER_CHAT | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002d5 | 725 | CHAT_GET_FIGHT_AREA_INFO | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002d7 | 727 | CHAT_GET_ZHAO_XIAN_MSG | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 000002ee | 750 | REVENUE | 1 | integer | raw | raw | CAPTURE_CONFIRMED |
| 0000030c | 780 | NOTICE_LIST | 14 | array | typed | typed | CLIENT_CONFIRMED |
| 00000367 | 871 | PROGRESS_GET_INFO | 2 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000378 | 888 | LOG_MUSIC_OPEN | 76 | boolean | raw | raw | CAPTURE_CONFIRMED |
| 0000059c | 1436 | COMMUNITY_GET_USER_TOKEN | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 000005a1 | 1441 | GET_PROGRESS_BEGIN_TIME | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000834 | 2100 | NOTIFY_CHAT_MSG | 3277 | array | typed | typed | CLIENT_CONFIRMED |
| 0000085d | 2141 | NOTIFY_PROGRESS_COMPLETED | 1 | integer | raw | raw | CAPTURE_CONFIRMED |
| 00000898 | 2200 | NOTIFY_SEND_NOTICE | 10724 | array | raw | typed | CLIENT_CONFIRMED |
| 00000907 | 2311 | SET_CHANNEL_CERTIFICATION | 48 | boolean | raw | raw | CAPTURE_CONFIRMED |
| 0000090f | 2319 | NOTIFY_SELF_UPGRADE | 6 | array | raw | raw | CAPTURE_CONFIRMED |
| 000009d9 | 2521 | NOTIFY_CCLIVE_SWITCH | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 000009e1 | 2529 | CCLIVE_GET_FOLLOW_LIST | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 000009e4 | 2532 | CCLIVE_LIVE_STOP_NOTIFY | 5 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000db5 | 3509 | UPDATE_MAX_POWER_IN_90_DAYS | 2 | boolean | raw | raw | CAPTURE_CONFIRMED |
| 00000eae | 3758 | MAIL_NOTIFY_GET_ALL | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000edf | 3807 | WORLD_EVENT_GET_ALL_EVENT | 2 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000f06 | 3846 | FRIEND_GROUP_GET_HISTORY_CHAT | 47 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000f45 | 3909 | GET_FAMILY_CHAT_HISTORY | 50 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000f58 | 3928 | FILE_PICKER_GET_TOKEN_DEFAULT | 5 | array | raw | raw | CAPTURE_CONFIRMED |
| 00000ff2 | 4082 | TRAVEL_SCENIC_SEND_FIRE_CHANGE | 3 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000103f | 4159 | QUERY_ARMY_RELATED_FORT | 30 | invalid | raw | raw | CAPTURE_CONFIRMED |
| 00001095 | 4245 | PREBOOK_SERVER_RECOMMEND | 1 | string | raw | raw | CAPTURE_CONFIRMED |
| 000010ea | 4330 | GET_LAND_NPC_ARMY | 79 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000110c | 4364 | - | 9 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000110d | 4365 | - | 4 | integer | raw | raw | CAPTURE_CONFIRMED |
| 00001122 | 4386 | - | 6 | array | raw | raw | CAPTURE_CONFIRMED |
| 00001363 | 4963 | SKILL_RECOMMENDATION | 2 | array | raw | raw | CAPTURE_CONFIRMED |
| 00001367 | 4967 | QUERY_WANTED_TO_REPOTR | 47 | array | raw | raw | CAPTURE_CONFIRMED |
| 00001368 | 4968 | CHECK_ADD_WEIXIN | 101 | array | raw | raw | CAPTURE_CONFIRMED |
| 000013a2 | 5026 | SEND_WORLD_SCENCE_FULL_INFO | 1616 | array | typed | typed | CLIENT_CONFIRMED |
| 000013a4 | 5028 | SEND_WORLD_SCENCE_CHANGE_INFO | 294 | array | typed | typed | CLIENT_CONFIRMED |
| 000013cd | 5069 | HELP_GUIDE_TIPS_LOG | 6 | integer | raw | raw | CAPTURE_CONFIRMED |
| 000013ce | 5070 | DAILY_REPORT_GET_DETAIL | 2 | array | raw | raw | CAPTURE_CONFIRMED |
| 000013da | 5082 | STRATEGY_HELP_GET | 47 | array | raw | raw | CAPTURE_CONFIRMED |
| 000013e3 | 5091 | UPDATE_GUIDE_RECORD | 51 | integer | raw | raw | CAPTURE_CONFIRMED |
| 00001412 | 5138 | CHECK_MAIMTENANCE_MAIL | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 0000145a | 5210 | GET_HERO_RECOMMEND_2 | 10 | array | raw | raw | CAPTURE_CONFIRMED |
| 000017a5 | 6053 | CHAT_UNION_PLAN_HISTORY_ID | 48 | object | raw | raw | CAPTURE_CONFIRMED |
| 0000184b | 6219 | ARMY_REINFORCE_STAY_CHECK | 4 | object | raw | raw | CAPTURE_CONFIRMED |
| 00001857 | 6231 | GET_BRIEF_BATTLE_REPORT_DETAIL | 1 | array | raw | raw | CAPTURE_CONFIRMED |
| 000018aa | 6314 | UNION_BUILDING_SPEED_UP_ADD | 154 | array | raw | raw | CAPTURE_CONFIRMED |
| 000018ad | 6317 | UNION_BUILDING_SPEED_UP_REMOVE | 305 | array | raw | raw | CAPTURE_CONFIRMED |
| 000018b6 | 6326 | UNION_RELATION_FULL_NOTIFY | 97 | array | raw | raw | CAPTURE_CONFIRMED |
| 000018b7 | 6327 | UNION_RELATION_CHANGE_NOTIFY | 16 | array | raw | raw | CAPTURE_CONFIRMED |
| 00001f49 | 8009 | - | 48 | array | raw | raw | CAPTURE_CONFIRMED |
| 00015f92 | 90002 | SYS_NOTIFY_EXCEPTION | 80 | array | raw | raw | CAPTURE_CONFIRMED |
| 00015f95 | 90005 | SYS_NOTIFY_DB_UPDATE_90005 | 974 | array | raw | raw | CAPTURE_CONFIRMED |
| 00015f96 | 90006 | SYS_PING_90006 | 2078 | array | raw | raw | CAPTURE_CONFIRMED |
| 00015f98 | 90008 | SYS_CHECK_SID_90008 | 5401 | integer | raw | raw | CAPTURE_CONFIRMED |
| 00016b4e | 93006 | SYS_MOD_PLAYER_ALL_MT | 10 | array | raw | raw | CAPTURE_CONFIRMED |
| 00016b4f | 93007 | SYS_MOD_MT_CHANGE | 23 | array | raw | raw | CAPTURE_CONFIRMED |
| 00016bac | 93100 | SYS_MOD_MT | 112 | array | raw | raw | CAPTURE_CONFIRMED |
| 00018248 | 98888 | SYS_NOTIFY_SID_98888 | 97 | invalid | raw | raw | CAPTURE_CONFIRMED |
| 00018697 | 99991 | SYS_LOGIN_CMD_99991 | 174 | array | raw | raw | CAPTURE_CONFIRMED |

A captured command is not automatically a typed business command.
UNKNOWN and raw entries remain available to the generic capture layer.
