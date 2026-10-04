-- WoW Mobile Touch UI v2
local frame = CreateFrame("Frame")
frame:RegisterEvent("PLAYER_ENTERING_WORLD")
frame:SetScript("OnEvent", function()
    -- Enlarge Blizzard UI text without enlarging the already touch-sized action bar.
    UIParent:SetScale(1.12)
    if MainMenuBar then
        MainMenuBar:SetScale(1.35 / 1.12)
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
end)
