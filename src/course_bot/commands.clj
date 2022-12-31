(ns course-bot.commands
  (:require [slash.command.structure :as scs]))


(def input-option (scs/option "input" "Your input" :string :required true))

(def fun-commands
  (scs/command
   "fun"
   "Fun commands"
   :default-member-permissions "2048"
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
  (scs/command
   "course"
   "Course commands"
   :default-member-permissions "2048" ;; Permision to send message
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
  (scs/command
   "sudo"
   "Admin commands"
   :default-member-permissions "0" ;; Admin only
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
     "update"
     "Update all popularity charts.")

    (scs/sub-command
     "override"
     "Override the internal course map with the supplied one. Supply no map to get help"
     :options
     [(scs/option "map" "course map" :string)])

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
     "Add a role to the allowed viewing list when creating channels"
     :options
     [(scs/option "role" "Role id" :string :required true)])

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
     "ping"
     "pong the ping.")]))

(def commands [fun-commands course-commands sudo-commands])
