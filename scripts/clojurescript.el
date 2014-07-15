(add-hook 'cider-repl-mode-hook 'paredit-mode)

(defun cider-start-browser-repl ()
  "Start a browser repl"
  (interactive)
  (let ((buffer (cider-current-repl-buffer)))
    (cider-eval "(browser-repl)"
                (cider-insert-eval-handler buffer)
                (cider-current-ns))))
