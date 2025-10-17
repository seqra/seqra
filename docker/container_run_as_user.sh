#! /bin/bash

USER_NAME="$1"
USER_PATH_COUNT="$2"

current_group_name=$(getent group "${CONTAINER_GID}" | cut -d: -f1)
current_user_name=$(getent passwd "${CONTAINER_UID}" | cut -d: -f1)

if [ -z "$current_group_name" ]; then
  groupmod -g "${CONTAINER_GID}" "$USER_NAME"
fi

if [ -z "$current_user_name" ]; then
  usermod -u "${CONTAINER_UID}" "$USER_NAME"
else
  USER_NAME="$current_user_name"
fi

USER_PATH_LAST_IDX="$((3 + USER_PATH_COUNT - 1))"
for idx in $(seq 3 $USER_PATH_LAST_IDX)
do
  loc="${@:idx:1}"
  chown -R "${CONTAINER_UID}:${CONTAINER_GID}" "$loc"
done

CMD_IDX="$((USER_PATH_LAST_IDX + 1))"
CMD_ARGS_IDX="$((USER_PATH_LAST_IDX + 2))"

su "$USER_NAME" -s "${@:CMD_IDX:1}" -- "${@:CMD_ARGS_IDX}"
