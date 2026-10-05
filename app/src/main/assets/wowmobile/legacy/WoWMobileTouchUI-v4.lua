-- WoW Mobile Touch UI v4 (Old Dream, 1280x720)
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
    -- Enlarge text objects without changing panel geometry or UIParent's scale.
    for _, entry in ipairs({
        {"GameFontNormal", 13}, {"GameFontHighlight", 13}, {"GameFontDisable", 13},
        {"GameTooltipText", 14}, {"GameTooltipHeaderText", 16}, {"GameTooltipTextSmall", 12}
    }) do
        local font = _G[entry[1]]
        if font then
            local path, size, flags = font:GetFont()
            if path and size and size < entry[2] then font:SetFont(path, entry[2], flags) end
        end
    end
    if FCF_SetChatWindowFontSize then
        for i = 1, 2 do
            local chatFrame = _G["ChatFrame" .. i]
            if chatFrame then
                local _, size = chatFrame:GetFont()
                if size and size < 16 then
                    FCF_SetChatWindowFontSize(nil, chatFrame, 16)
                end
            end
        end
    end
end
frame:SetScript("OnEvent", updateLayout)
