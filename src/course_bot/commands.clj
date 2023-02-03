(ns course-bot.commands
  (:require [slash.command.structure :as scs]
            [slash.util :refer [omission-map]]
            [discljord.permissions :as d-perms]))



(def input-option (scs/option "input" "Your input" :string :required true))

(defn command
  "Create a top level command.

  See https://discord.com/developers/docs/interactions/slash-commands#application-command-object-application-command-structure.
  `:type` must be one of the keys in [[command-types]], if given."
  [name description & {:keys [default-member-permissions guild-id options type]}]
  (omission-map
   :name name
   :description description
   :options options
   :guild_id guild-id
   :default_member_permissions default-member-permissions
   :type (some-> type scs/command-types)))

(def fun-commands
  (command
   "fun"
   "Fun commands"
   :default-member-permissions (d-perms/permission-int '(:send-messages))
   :options
   [(scs/sub-command
     "reverse-input"
     "Reverse the input"
     :options
     [input-option
      (scs/option "words" "Reverse words instead of characters?" :boolean)])
    (scs/sub-command
     "mock"
     "Spongebob-mock the input"
     :options
     [input-option])]))

(def course-commands
  (command
   "course"
   "Course commands"
   :default-member-permissions (d-perms/permission-int '(:send-messages)) ;; Permision to send message
   :options
   [(scs/sub-command
     "register"
     "Register your interest in the course"
     :options
     [(assoc input-option :autocomplete true)])
    (scs/sub-command
     "deregister"
     "Deregister your interest in the course"
     :options
     [(assoc input-option :autocomplete true)])]))

(def sudo-commands
  (command
   "sudo"
   "Admin commands"
   :default-member-permissions (d-perms/permission-int '(:manage-channels :manage-roles)) ;; Admin only
   :options
   [(scs/sub-command
     "reset"
     "Reset course interest counts")
    (scs/sub-command
     "clean"
     "Remove courses with interest below threshold"
     :options
     [(scs/option "threshold" "Threshold" :number :required true)])
    (scs/sub-command
     "set-interest"
     "DEBUG Set interest level of a course"
     :options
     [(scs/option "course" "Course code" :string :required true)
      (scs/option "n" "Level to set interest to." :number :required true)])
    (scs/sub-command
     "dump"
     "Dump the internal map")

    (scs/sub-command
     "create-roles-and-channels"
     "Create all the roles and channels present in the map")

    (scs/sub-command
     "remove-roles-and-channels"
     "Remove all the roles and channels present in the map")

    (scs/sub-command
     "chart"
     "Send a message containing the popularity chart.")
    (scs/sub-command
     "update-charts"
     "Update all popularity charts.")

    (scs/sub-command
     "override"
     "Override the internal course map with the supplied one. Supply no map to get help"
     :options
     [(scs/option "map" "course map" :string)])

    (scs/sub-command
     "config"
     "Override the config with the supplied one. Supply no map to get the current value"
     :options
     [(scs/option "value" "Config." :string)])

    (scs/sub-command
     "course-regex"
     "Override the course-regex with the supplied one. Supply no map to get the current value"
     :options
     [(scs/option "value" "java regex" :string)])

    (scs/sub-command
     "auto-enroll"
     "Change the auto-enroll value"
     :options
     [(scs/option "value" "Value. Provide none to query" :boolean)])

    (scs/sub-command
     "auto-save"
     "Change the auto-save value"
     :options
     [(scs/option "value" "Value. Provide none to query" :boolean)])

    (scs/sub-command
     "additional-roles"
     "Add a role to the allowed viewing list when creating channels. Supply none to view it"
     :options
     [(scs/option "role" "Role id" :string)])

    (scs/sub-command
     "image-host-channel"
     "Channel id where graph images will be uploaded to"
     :options
     [(scs/option "value" "Channel id" :string)])

    (scs/sub-command
     "embed-colour"
     "Change or query the embed colour. Discord colour codes only"
     :options
     [(scs/option "value" "Colour" :string)])

    (scs/sub-command
     "remove-additional-roles"
     "Remove a role to the allowed viewing list when creating channels"
     :options
     [(scs/option "role" "Role id" :string :required true)])

    (scs/sub-command
     "force-register"
     "Register another user to a course for them"
     :options
     [(scs/option "user" "id of the user (right-click 'Copy Id')" :string :required true)
      (scs/option "course" "Course code" :string :required true)])

    (scs/sub-command
     "force-deregister"
     "Deregister another user from a course for them"
     :options
     [(scs/option "user" "id of the user (right-click 'Copy Id')" :string :required true)
      (scs/option "course" "Course code" :string :required true)])

    (scs/sub-command
     "enroll-all"
     "Enroll all those registered")

    (scs/sub-command
     "unenroll-all"
     "Unenroll all those registered")

    (scs/sub-command
     "save"
     "Just save everything to disk now, *NOT* intended for use other than debug")

    (scs/sub-command
     "ping"
     "pong the ping.")]))

(def commands [fun-commands course-commands sudo-commands])
