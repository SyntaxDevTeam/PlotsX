package pl.syntaxdevteam.plotsx.interaction

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import org.bukkit.entity.Player
import java.time.Duration

/** Loaded reflectively only when Paper's dialog API exists. */
@Suppress("UnstableApiUsage")
class PaperDialogBackend : DialogBackend {
    override fun show(player: Player, title: Component, body: List<Component>, initial: String?,
                      yes: Component, no: Component, reply: (String?) -> Unit) {
        val options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(60)).build()
        val accept = ActionButton.builder(yes).action(DialogAction.customClick({ response, audience ->
            if (audience is Player && audience.uniqueId == player.uniqueId)
                reply(if (initial == null) "" else response.getText("name") ?: "")
        }, options)).build()
        val cancel = ActionButton.builder(no).action(DialogAction.customClick({ _, audience ->
            if (audience is Player && audience.uniqueId == player.uniqueId) reply(null)
        }, options)).build()
        val dialog = Dialog.create { builder ->
            builder.empty().base(DialogBase.builder(title)
                .canCloseWithEscape(true)
                .body(body.map { DialogBody.plainMessage(it) })
                .inputs(if (initial == null) emptyList() else listOf(
                    DialogInput.text("name", title).initial(initial.take(255)).maxLength(255).build()))
                .build())
                .type(DialogType.confirmation(accept, cancel))
        }
        player.showDialog(dialog)
    }
}
