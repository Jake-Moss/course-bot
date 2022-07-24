(ns course-bot.commands
  (:require [slash.command.structure :as scs]))


(def input-option (scs/option "input" "Your input" :string :required true))

(def fun-commands
  (scs/command
   "fun"
   "Fun commands"
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
   :options
   [(scs/sub-command
     "register"
     "Register your interest in the course"
     :options
     [(assoc input-option :autocomplete true)])]))

(def sudo-commands
  (scs/command
   "sudo"
   "Admin commands"
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
     "chart"
     "Send a message containing the popularity chart.")

    (scs/sub-command
     "ping"
     "pong the ping.")]))

(def commands [fun-commands course-commands sudo-commands])
