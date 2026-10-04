-- WoW Mobile Touch UI v3
local frame = CreateFrame("Frame")
frame:RegisterEvent("PLAYER_ENTERING_WORLD")
frame:RegisterEvent("DISPLAY_SIZE_CHANGED")
frame:RegisterEvent("UI_SCALE_CHANGED")
frame:RegisterEvent("PLAYER_REGEN_ENABLED")
local function updateLayout()
    -- Leave UIParent under the game's control so panels and bag anchors agree.
    -- Fit the entire bottom bar, including its backpack buttons, inside the screen.
    if MainMenuBar and not InCombatLockdown() then
        local available = UIParent:GetWidth() - 48
        MainMenuBar:SetScale(math.min(1.35, available / MainMenuBar:GetWidth()))
    end
    if FCF_SetChatWindowFontSize then
        for i = 1, 2 do
            local chatFrame = _G["ChatFrame" .. i]
            if chatFrame then
                local _, size = chatFrame:GetFont()
                if size and size < 14 then
                    FCF_SetChatWindowFontSize(nil, chatFrame, 14)
                end
            end
        end
    end
end
frame:SetScript("OnEvent", updateLayout)
